const configuredOrigin = import.meta.env.VITE_PUBLIC_ORIGIN?.trim()

function publicUrl(path, currentOrigin) {
  const origin = configuredOrigin || currentOrigin
  if (configuredOrigin) {
    const parsed = new URL(configuredOrigin)
    if (parsed.protocol !== 'https:' || parsed.origin !== configuredOrigin) {
      throw new Error('VITE_PUBLIC_ORIGIN must be an HTTPS origin without a path')
    }
  }
  return new URL(path, origin)
}

export function registrationUrl(code, currentOrigin) {
  return publicUrl(`/activities/${code}`, currentOrigin)
}

export function checkInUrl(code, currentOrigin) {
  return publicUrl(`/check-in/${code}`, currentOrigin)
}
