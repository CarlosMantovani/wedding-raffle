import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CalendarClock, Save, Settings } from 'lucide-react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextInput } from '../../components/ui/TextInput';
import { raffleConfigService } from '../../services/raffleConfigService';
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '../../utils/dateTime';
import { getPortugueseErrorMessage } from '../../utils/errorMessages';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import type { ApiError } from '../../types/api';
import type { RaffleComboResponse } from '../../types/transaction';
import {
  raffleComboSchema,
  raffleConfigSchema,
  scheduledDrawSchema,
  type RaffleComboFormData,
  type RaffleConfigFormData,
  type ScheduledDrawFormData,
} from './schemas';

export function AdminSettingsPage() {
  const queryClient = useQueryClient();
  const {
    formState: { errors, isValid },
    handleSubmit,
    register,
    reset,
  } = useForm<RaffleConfigFormData>({
    defaultValues: { unitPrice: 0 },
    mode: 'onChange',
    resolver: zodResolver(raffleConfigSchema),
  });
  const {
    formState: { errors: scheduledDrawErrors, isValid: isScheduledDrawValid },
    handleSubmit: handleScheduledDrawSubmit,
    register: registerScheduledDraw,
    reset: resetScheduledDraw,
  } = useForm<ScheduledDrawFormData>({
    defaultValues: { scheduledDrawAt: '' },
    mode: 'onChange',
    resolver: zodResolver(scheduledDrawSchema),
  });

  const configQuery = useQuery({
    queryKey: ['admin-raffle-config'],
    queryFn: raffleConfigService.getConfig,
  });

  useEffect(() => {
    if (configQuery.data) {
      reset({ unitPrice: Number(configQuery.data.unitPrice) });
      resetScheduledDraw({
        scheduledDrawAt: toDateTimeLocalValue(configQuery.data.scheduledDrawAt),
      });
    }
  }, [configQuery.data, reset, resetScheduledDraw]);

  const updateUnitPriceMutation = useMutation({
    mutationFn: (data: RaffleConfigFormData) =>
      raffleConfigService.updateUnitPrice({ unitPrice: data.unitPrice.toFixed(2) }),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin-raffle-config'], data);
      reset({ unitPrice: Number(data.unitPrice) });
    },
  });

  const updateScheduledDrawMutation = useMutation({
    mutationFn: (data: ScheduledDrawFormData) =>
      raffleConfigService.updateScheduledDrawAt({
        scheduledDrawAt: fromDateTimeLocalValue(data.scheduledDrawAt),
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin-raffle-config'], data);
      resetScheduledDraw({ scheduledDrawAt: toDateTimeLocalValue(data.scheduledDrawAt) });
    },
  });

  return (
    <main className="min-h-screen bg-cream text-charcoal">
      <header className="bg-charcoal px-6 py-4 text-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-4">
          <div>
            <p className="font-serif text-2xl font-bold">
              Presente <span className="italic text-gold">Premiado</span>
            </p>
            <p className="mt-1 text-xs text-white/55">Configurações da rifa</p>
          </div>
          <a
            className="inline-flex items-center gap-2 text-sm font-semibold text-white/70 hover:text-white"
            href="/admin"
          >
            <ArrowLeft aria-hidden="true" className="h-4 w-4" />
            Voltar
          </a>
        </div>
      </header>

      <section className="mx-auto grid max-w-4xl gap-6 px-6 py-8 md:grid-cols-[1fr_0.9fr]">
        <div className="space-y-6">
          <Card>
            <form
              className="space-y-5"
              onSubmit={handleSubmit((data) => updateUnitPriceMutation.mutate(data))}
            >
              <div>
                <h1 className="font-serif text-2xl font-bold">Preço unitário</h1>
                <p className="mt-1 text-sm text-warm-gray">
                  O novo valor passa a valer apenas para novas cotações e novas transações.
                </p>
              </div>

              <TextInput
                id="raffle-unit-price"
                label="Valor por número"
                min="0.01"
                step="0.01"
                type="number"
                error={errors.unitPrice?.message}
                {...register('unitPrice')}
              />

              {updateUnitPriceMutation.isSuccess ? (
                <p
                  className="rounded-lg border border-olive/20 bg-olive/10 px-4 py-3 text-sm font-semibold text-olive"
                  role="status"
                >
                  Preço atualizado com sucesso.
                </p>
              ) : null}

              {updateUnitPriceMutation.isError ? (
                <p
                  className="rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark"
                  role="alert"
                >
                  {getApiErrorMessage(
                    updateUnitPriceMutation.error,
                    'Não foi possível atualizar o preço.',
                  )}
                </p>
              ) : null}

              <Button
                disabled={!isValid || configQuery.isLoading}
                isLoading={updateUnitPriceMutation.isPending}
                type="submit"
              >
                <Save aria-hidden="true" className="h-5 w-5" />
                Salvar preço
              </Button>
            </form>
          </Card>

          <Card>
            <form
              className="space-y-5"
              onSubmit={handleScheduledDrawSubmit((data) =>
                updateScheduledDrawMutation.mutate(data),
              )}
            >
              <div>
                <h2 className="font-serif text-2xl font-bold">Data do sorteio</h2>
                <p className="mt-1 text-sm text-warm-gray">
                  Essa data alimenta a contagem regressiva exibida na tela inicial.
                </p>
              </div>

              <TextInput
                id="raffle-scheduled-draw-at"
                label="Data e horário"
                type="datetime-local"
                error={scheduledDrawErrors.scheduledDrawAt?.message}
                {...registerScheduledDraw('scheduledDrawAt')}
              />

              {updateScheduledDrawMutation.isSuccess ? (
                <p
                  className="rounded-lg border border-olive/20 bg-olive/10 px-4 py-3 text-sm font-semibold text-olive"
                  role="status"
                >
                  Data do sorteio atualizada com sucesso.
                </p>
              ) : null}

              {updateScheduledDrawMutation.isError ? (
                <p
                  className="rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark"
                  role="alert"
                >
                  Não foi possível atualizar a data do sorteio.
                </p>
              ) : null}

              <Button
                disabled={!isScheduledDrawValid || configQuery.isLoading}
                isLoading={updateScheduledDrawMutation.isPending}
                type="submit"
              >
                <CalendarClock aria-hidden="true" className="h-5 w-5" />
                Salvar data
              </Button>
            </form>
          </Card>

          <Card>
            <div className="space-y-5">
              <div>
                <h2 className="font-serif text-2xl font-bold">Combos promocionais</h2>
                <p className="mt-1 text-sm text-warm-gray">
                  As quantidades são fixas. Altere apenas preço, disponibilidade e ordem.
                </p>
              </div>
              {configQuery.data?.combos.map((combo) => (
                <RaffleComboSettings
                  combo={combo}
                  key={combo.id}
                  onUpdated={(data) => queryClient.setQueryData(['admin-raffle-config'], data)}
                />
              ))}
            </div>
          </Card>
        </div>

        <Card className="bg-blush shadow-none">
          {configQuery.isLoading ? (
            <p className="py-10 text-center text-sm text-warm-gray">Carregando configurações...</p>
          ) : null}

          {configQuery.isError ? (
            <p
              className="rounded-lg border border-terracotta/30 bg-white px-4 py-3 text-sm text-terracotta-dark"
              role="alert"
            >
              Não foi possível carregar o preço atual.
            </p>
          ) : null}

          {configQuery.data ? (
            <div className="space-y-5">
              <div>
                <Settings aria-hidden="true" className="h-10 w-10 text-terracotta" />
                <p className="mt-4 text-xs font-bold uppercase tracking-wide text-warm-gray">
                  Preço vigente
                </p>
                <p className="mt-2 font-serif text-4xl font-bold text-charcoal">
                  {formatCurrency(configQuery.data.unitPrice)}
                </p>
              </div>

              <div className="rounded-lg bg-white/70 px-4 py-3 text-sm leading-relaxed text-warm-gray">
                Transações já criadas mantêm o valor com que nasceram. Esta configuração só altera o
                preço usado daqui em diante.
              </div>

              {configQuery.data.updatedAt ? (
                <p className="text-xs text-warm-gray">
                  Última atualização: {formatDateTime(configQuery.data.updatedAt)}
                </p>
              ) : null}

              <div className="rounded-lg bg-white/70 px-4 py-3">
                <p className="text-xs font-bold uppercase tracking-wide text-warm-gray">Sorteio</p>
                <p className="mt-2 text-sm font-semibold text-charcoal">
                  {configQuery.data.scheduledDrawAt
                    ? formatDateTime(configQuery.data.scheduledDrawAt)
                    : 'Ainda não configurado'}
                </p>
              </div>
            </div>
          ) : null}
        </Card>
      </section>
    </main>
  );
}

function RaffleComboSettings({
  combo,
  onUpdated,
}: {
  combo: RaffleComboResponse;
  onUpdated: (data: Awaited<ReturnType<typeof raffleConfigService.updateCombo>>) => void;
}) {
  const {
    formState: { errors, isValid },
    handleSubmit,
    register,
    reset,
  } = useForm<RaffleComboFormData>({
    defaultValues: {
      active: combo.active,
      displayOrder: combo.displayOrder,
      highlightBestValue: combo.highlightBestValue,
      highlightMostChosen: combo.highlightMostChosen,
      price: Number(combo.price),
    },
    mode: 'onChange',
    resolver: zodResolver(raffleComboSchema),
  });

  useEffect(() => {
    reset({
      active: combo.active,
      displayOrder: combo.displayOrder,
      highlightBestValue: combo.highlightBestValue,
      highlightMostChosen: combo.highlightMostChosen,
      price: Number(combo.price),
    });
  }, [combo, reset]);

  const updateMutation = useMutation({
    mutationFn: (data: RaffleComboFormData) =>
      raffleConfigService.updateCombo(combo.id, {
        active: data.active,
        displayOrder: data.displayOrder,
        highlightBestValue: data.highlightBestValue,
        highlightMostChosen: data.highlightMostChosen,
        price: data.price.toFixed(2),
      }),
    onSuccess: (data) => {
      onUpdated(data);
      const updatedCombo = data.combos.find((item) => item.id === combo.id);
      if (updatedCombo) {
        reset({
          active: updatedCombo.active,
          displayOrder: updatedCombo.displayOrder,
          highlightBestValue: updatedCombo.highlightBestValue,
          highlightMostChosen: updatedCombo.highlightMostChosen,
          price: Number(updatedCombo.price),
        });
      }
    },
  });

  return (
    <form
      className="rounded-xl border border-line bg-white/70 p-4"
      onSubmit={handleSubmit((data) => updateMutation.mutate(data))}
    >
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="font-serif text-xl font-bold">{combo.quantity} números</p>
          <p className="mt-1 text-xs text-warm-gray">
            Valor normal: {formatCurrency(combo.regularPrice)}
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm font-semibold text-charcoal">
          <input className="h-4 w-4 accent-green" type="checkbox" {...register('active')} />
          Ativo
        </label>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <TextInput
          error={errors.price?.message}
          id={`combo-price-${combo.id}`}
          label="Preço"
          min="0.01"
          step="0.01"
          type="number"
          {...register('price')}
        />
        <TextInput
          error={errors.displayOrder?.message}
          id={`combo-order-${combo.id}`}
          label="Ordem"
          min="0"
          step="1"
          type="number"
          {...register('displayOrder')}
        />
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="flex items-center gap-2 text-sm font-semibold text-charcoal">
          <input
            className="h-4 w-4 accent-green"
            type="checkbox"
            {...register('highlightMostChosen')}
          />
          Mais escolhido
        </label>
        <label className="flex items-center gap-2 text-sm font-semibold text-charcoal">
          <input
            className="h-4 w-4 accent-green"
            type="checkbox"
            {...register('highlightBestValue')}
          />
          Melhor valor
        </label>
      </div>
      {updateMutation.isError ? (
        <p className="mt-3 rounded-lg border border-terracotta/30 bg-blush px-3 py-2 text-sm text-terracotta-dark">
          {getApiErrorMessage(updateMutation.error, 'Não foi possível atualizar o combo.')}
        </p>
      ) : null}
      {updateMutation.isSuccess ? (
        <p className="mt-3 text-sm font-semibold text-olive">Combo atualizado com sucesso.</p>
      ) : null}
      <Button
        className="mt-4"
        disabled={!isValid}
        isLoading={updateMutation.isPending}
        type="submit"
      >
        <Save aria-hidden="true" className="h-4 w-4" />
        Salvar combo
      </Button>
    </form>
  );
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'message' in error) {
    const apiError = error as Pick<ApiError, 'message' | 'status'>;
    return getPortugueseErrorMessage(apiError.message, apiError.status);
  }
  return fallback;
}
