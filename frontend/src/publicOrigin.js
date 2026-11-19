const configuredOrigin = import.meta.env.VITE_PUBLIC_ORIGIN?.trim()

export function registrationUrl(code, currentOrigin) {
  const origin = configuredOrigin || currentOrigin
  if (configuredOrigin) {
    const parsed = new URL(configuredOrigin)
    if (parsed.protocol !== 'https:' || parsed.origin !== configuredOrigin) {
      throw new Error('VITE_PUBLIC_ORIGIN must be an HTTPS origin without a path')
    }
  }
  return new URL(`/activities/${code}`, origin)
}
