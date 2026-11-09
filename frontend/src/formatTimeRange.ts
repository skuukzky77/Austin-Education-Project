/** Input values are Melbourne local date-times already formatted by the server. */
export function formatTimeRange(start: string, end: string): string {
  const endLabel =
    start.slice(0, 10) === end.slice(0, 10)
      ? end.slice(11, 16)
      : end.replace("T", " ");
  return `${start.replace("T", " ")} – ${endLabel}`;
}
