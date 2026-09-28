/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        // Semantic tokens; light / dark pairs are picked via `dark:` variants.
        bg: { DEFAULT: '#F6F6F8', dark: '#0B0B0F' },
        card: { DEFAULT: '#FFFFFF', dark: '#16161D' },
        line: { DEFAULT: '#E4E4EA', dark: '#26262F' },
        ink: { DEFAULT: '#101014', dark: '#F4F4F6' },
        muted: { DEFAULT: '#5E5E6B', dark: '#A1A1AE' },
        accent: { DEFAULT: '#D6246E', dark: '#FF4F93' },
        good: { DEFAULT: '#12805C', dark: '#3DD68C' },
        warn: { DEFAULT: '#A55E00', dark: '#FFB224' },
        bad: { DEFAULT: '#C62828', dark: '#FF6369' },
      },
      spacing: {
        // 4-pt scale is Tailwind's default; these are the named layout steps.
        gutter: '16px',
        section: '24px',
      },
      borderRadius: {
        card: '18px',
      },
    },
  },
  plugins: [],
};
