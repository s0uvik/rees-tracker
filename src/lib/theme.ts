import { useColorScheme } from 'react-native';

/**
 * Raw colour values for places className can't reach (charts, icons, native
 * props). Keep in sync with the tokens in tailwind.config.js.
 */
const light = {
  bg: '#F6F6F8',
  card: '#FFFFFF',
  line: '#E4E4EA',
  ink: '#101014',
  muted: '#5E5E6B',
  accent: '#D6246E',
  accentSoft: '#F4B8CF',
  good: '#12805C',
  warn: '#A55E00',
  bad: '#C62828',
  // Floating badge fills (match BadgeColors.kt)
  badgeNeutral: 'rgba(32,33,36,0.9)',
  badgeAmber: 'rgba(178,106,0,0.95)',
  badgeRed: 'rgba(198,40,40,0.95)',
};

const dark: typeof light = {
  bg: '#0B0B0F',
  card: '#16161D',
  line: '#26262F',
  ink: '#F4F4F6',
  muted: '#A1A1AE',
  accent: '#FF4F93',
  accentSoft: '#5A2140',
  good: '#3DD68C',
  warn: '#FFB224',
  bad: '#FF6369',
  badgeNeutral: 'rgba(32,33,36,0.9)',
  badgeAmber: 'rgba(178,106,0,0.95)',
  badgeRed: 'rgba(198,40,40,0.95)',
};

export type Palette = typeof light;

export function usePalette(): Palette {
  return useColorScheme() === 'light' ? light : dark;
}

export function useIsDark(): boolean {
  return useColorScheme() !== 'light';
}
