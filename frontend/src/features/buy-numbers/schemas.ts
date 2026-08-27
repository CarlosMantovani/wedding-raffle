import { z } from 'zod';

import { isValidCpf } from '../../utils/cpf';

export const buyerSchema = z.object({
  name: z.string().trim().min(1, 'Informe seu nome.'),
  phone: z
    .string()
    .trim()
    .min(1, 'Informe seu telefone.')
    .refine((value) => {
      const digits = value.replace(/\D/g, '');
      return digits.length === 10 || digits.length === 11;
    }, 'Informe um telefone com DDD.'),
  cpf: z
    .string()
    .trim()
    .min(1, 'Informe seu CPF.')
    .regex(/^(?:\d{11}|\d{3}\.\d{3}\.\d{3}-\d{2})$/, 'Informe um CPF com 11 dígitos.')
    .refine(isValidCpf, 'Informe um CPF válido.'),
  email: z
    .string()
    .trim()
    .refine((value) => value === '' || z.string().email().safeParse(value).success, {
      message: 'Informe um e-mail válido.',
    }),
});

export const recoverySchema = z.object({
  phone: z
    .string()
    .trim()
    .min(1, 'Informe seu telefone.')
    .refine((value) => {
      const digits = value.replace(/\D/g, '');
      return digits.length === 10 || digits.length === 11;
    }, 'Informe um telefone com DDD.'),
  recoveryCode: z
    .string()
    .trim()
    .regex(/^\d{4}$/, 'Informe o código de 4 dígitos.'),
});

export type BuyerFormData = z.infer<typeof buyerSchema>;
export type RecoveryFormData = z.infer<typeof recoverySchema>;
