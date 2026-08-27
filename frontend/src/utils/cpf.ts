const CPF_PATTERN = /^(?:\d{11}|\d{3}\.\d{3}\.\d{3}-\d{2})$/;

export function normalizeCpf(value: string): string {
  return value.replace(/\D/g, '').slice(0, 11);
}

export function formatCpf(value: string): string {
  const digits = normalizeCpf(value);
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
  if (digits.length <= 9) {
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
  }
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
}

export function isValidCpf(value: string): boolean {
  if (!CPF_PATTERN.test(value)) return false;

  const digits = normalizeCpf(value);
  if (new Set(digits).size === 1) return false;

  return (
    calculateDigit(digits, 9) === Number(digits[9]) &&
    calculateDigit(digits, 10) === Number(digits[10])
  );
}

function calculateDigit(digits: string, length: number): number {
  let sum = 0;
  for (let index = 0; index < length; index += 1) {
    sum += Number(digits[index]) * (length + 1 - index);
  }
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}
