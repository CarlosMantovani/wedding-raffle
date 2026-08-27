export type AdminRole = 'MASTER' | 'CASHIER';

export interface AuthLoginRequest {
  username: string;
  password: string;
}

export interface AuthLoginResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  roles?: AdminRole[];
}

export interface AdminSession {
  tokenType: string;
  accessToken: string;
  expiresAt: number;
  roles: AdminRole[];
}
