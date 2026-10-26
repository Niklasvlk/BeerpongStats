// Rot (0 %) bis Grün (100 %), wie im bisherigen Thymeleaf-UI
export function winRateColor(winRate: number): string {
  return `rgb(${255 - winRate * 255},${winRate * 255},0)`;
}
