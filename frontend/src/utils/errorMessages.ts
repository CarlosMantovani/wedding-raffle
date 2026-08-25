const DEFAULT_ERROR_MESSAGE = 'Não foi possível concluir a operação. Tente novamente em alguns instantes.';

const STATUS_ERROR_MESSAGES: Record<number, string> = {
  400: 'Verifique os dados informados e tente novamente.',
  401: 'Sessão expirada. Faça login novamente.',
  403: 'Você não tem permissão para executar esta ação.',
  404: 'Registro não encontrado.',
  409: 'Não foi possível concluir a operação por conflito com o estado atual.',
  422: 'Verifique os dados informados e tente novamente.',
  500: 'Não foi possível concluir a operação. Tente novamente em alguns instantes.',
};

const EXACT_MESSAGE_TRANSLATIONS: Record<string, string> = {
  'Access Denied': 'Você não tem permissão para executar esta ação.',
  'Bad credentials': 'Usuário ou senha inválidos.',
  'Full authentication is required to access this resource': 'Sessão expirada. Faça login novamente.',
  'Network Error': 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
  'Request failed with status code 400': STATUS_ERROR_MESSAGES[400],
  'Request failed with status code 401': STATUS_ERROR_MESSAGES[401],
  'Request failed with status code 403': STATUS_ERROR_MESSAGES[403],
  'Request failed with status code 404': STATUS_ERROR_MESSAGES[404],
  'Request failed with status code 409': STATUS_ERROR_MESSAGES[409],
  'Request failed with status code 422': STATUS_ERROR_MESSAGES[422],
  'Request failed with status code 500': STATUS_ERROR_MESSAGES[500],
  'Validation failed': 'Verifique os dados informados e tente novamente.',
};

const PARTIAL_MESSAGE_TRANSLATIONS: Array<[RegExp, string]> = [
  [/must be greater than 0/i, 'Informe um valor maior que zero.'],
  [/must be greater than or equal to 0/i, 'Informe um valor maior ou igual a zero.'],
  [/must be less than or equal to (\d+)/i, 'Informe um valor dentro do limite permitido.'],
  [/must not be blank/i, 'Preencha este campo.'],
  [/must not be empty/i, 'Preencha este campo.'],
  [/must not be null/i, 'Preencha este campo.'],
  [/must be a well-formed email address/i, 'Informe um e-mail válido.'],
  [/failed to fetch/i, 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'],
  [/network error/i, 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.'],
];

export function getPortugueseErrorMessage(message?: string | null, status?: number) {
  const normalizedMessage = message?.trim();

  if (!normalizedMessage) {
    return getStatusErrorMessage(status);
  }

  const exactTranslation = EXACT_MESSAGE_TRANSLATIONS[normalizedMessage];
  if (exactTranslation) {
    return exactTranslation;
  }

  const partialTranslation = PARTIAL_MESSAGE_TRANSLATIONS.find(([pattern]) =>
    pattern.test(normalizedMessage),
  );
  if (partialTranslation) {
    return partialTranslation[1];
  }

  if (looksLikeEnglish(normalizedMessage)) {
    return getStatusErrorMessage(status);
  }

  return normalizedMessage;
}

function getStatusErrorMessage(status?: number) {
  return status ? STATUS_ERROR_MESSAGES[status] ?? DEFAULT_ERROR_MESSAGE : DEFAULT_ERROR_MESSAGE;
}

function looksLikeEnglish(message: string) {
  return /\b(the|must|required|invalid|failed|error|denied|forbidden|unauthorized|expected|received|request|resource|credentials)\b/i.test(
    message,
  );
}
