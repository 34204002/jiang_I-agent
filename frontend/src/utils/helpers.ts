export function safeJsonParse<T>(raw: string | null, fallback: T): T {
    try {
        return (JSON.parse(raw || '') || fallback) as T
    } catch {
        return fallback
    }
}
