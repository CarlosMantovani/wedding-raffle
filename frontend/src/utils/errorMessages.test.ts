import { getPortugueseErrorMessage } from './errorMessages';

describe('getPortugueseErrorMessage', () => {
  it('keeps Portuguese messages unchanged', () => {
    expect(getPortugueseErrorMessage('Informe um valor válido.', 400)).toBe(
      'Informe um valor válido.',
    );
  });

  it('translates known English API messages', () => {
    expect(getPortugueseErrorMessage('Bad credentials', 401)).toBe('Usuário ou senha inválidos.');
    expect(getPortugueseErrorMessage('must be greater than 0', 400)).toBe(
      'Informe um valor maior que zero.',
    );
  });

  it('uses Portuguese status fallback for unknown English messages', () => {
    expect(getPortugueseErrorMessage('Invalid raffle configuration', 400)).toBe(
      'Verifique os dados informados e tente novamente.',
    );
  });
});
