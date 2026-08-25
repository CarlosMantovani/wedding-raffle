interface StepProgressProps {
  currentStep: 1 | 2 | 3 | 4;
}

const steps = [
  { id: 1, label: 'Seus dados' },
  { id: 2, label: 'Números' },
  { id: 3, label: 'Mensagem' },
  { id: 4, label: 'Revisão' },
] as const;

export function StepProgress({ currentStep }: StepProgressProps) {
  return (
    <ol aria-label="Progresso da compra" className="mx-auto grid w-full grid-cols-4 gap-1">
      {steps.map((step, index) => {
        const isDone = step.id < currentStep;
        const isCurrent = step.id === currentStep;

        return (
          <li className="flex min-w-0 items-center gap-1" key={step.id}>
            <div className="flex flex-col items-center gap-1">
              <span
                className={`grid h-8 w-8 place-items-center rounded-full text-xs font-bold ${
                  isDone || isCurrent ? 'bg-gold text-charcoal' : 'bg-ivory-deep text-warm-gray'
                }`}
              >
                {isDone ? '✓' : step.id}
              </span>
              <span
                className={`text-center text-[10px] leading-tight ${
                  isCurrent ? 'font-semibold text-green' : 'text-warm-gray'
                }`}
              >
                {step.label}
              </span>
            </div>
            {index < steps.length - 1 ? (
              <span aria-hidden="true" className="h-px min-w-0 flex-1 bg-line" />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
