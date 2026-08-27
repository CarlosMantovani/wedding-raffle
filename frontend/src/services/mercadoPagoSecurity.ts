const DEVICE_ID_PATTERN = /^[\x21-\x7e]{1,256}$/;

type MercadoPagoWindow = Window & { MP_DEVICE_SESSION_ID?: unknown };

export function getMercadoPagoDeviceId(): string | undefined {
  const value = (window as MercadoPagoWindow).MP_DEVICE_SESSION_ID;
  if (typeof value !== 'string') return undefined;

  const deviceId = value.trim();
  return DEVICE_ID_PATTERN.test(deviceId) ? deviceId : undefined;
}
