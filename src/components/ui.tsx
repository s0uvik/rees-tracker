import type { ReactNode } from 'react';
import { Pressable, Switch, Text, View, type PressableProps } from 'react-native';

import { usePalette } from '@/lib/theme';

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <View
      className={`rounded-card border border-line bg-card p-4 dark:border-line-dark dark:bg-card-dark ${className}`}
    >
      {children}
    </View>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <Text className="mb-2 mt-section px-1 text-xs font-semibold uppercase tracking-wider text-muted dark:text-muted-dark">
      {children}
    </Text>
  );
}

export function Muted({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <Text className={`text-sm text-muted dark:text-muted-dark ${className}`}>{children}</Text>;
}

type ButtonProps = Omit<PressableProps, 'children'> & {
  label: string;
  variant?: 'primary' | 'secondary' | 'danger';
};

export function Button({ label, variant = 'primary', disabled, ...rest }: ButtonProps) {
  const base = 'items-center justify-center rounded-2xl px-5 py-3.5';
  const tone =
    variant === 'primary'
      ? 'bg-accent dark:bg-accent-dark'
      : variant === 'danger'
        ? 'border border-bad dark:border-bad-dark'
        : 'border border-line bg-card dark:border-line-dark dark:bg-card-dark';
  const text =
    variant === 'primary'
      ? 'text-white'
      : variant === 'danger'
        ? 'text-bad dark:text-bad-dark'
        : 'text-ink dark:text-ink-dark';
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      className={`${base} ${tone} ${disabled ? 'opacity-40' : 'active:opacity-80'}`}
      {...rest}
    >
      <Text className={`text-base font-semibold ${text}`}>{label}</Text>
    </Pressable>
  );
}

export function Row({
  title,
  subtitle,
  right,
  disabled,
}: {
  title: string;
  subtitle?: string;
  right?: ReactNode;
  disabled?: boolean;
}) {
  return (
    <View className={`flex-row items-center py-3 ${disabled ? 'opacity-40' : ''}`}>
      <View className="flex-1 pr-3">
        <Text className="text-base text-ink dark:text-ink-dark">{title}</Text>
        {subtitle ? <Muted className="mt-0.5">{subtitle}</Muted> : null}
      </View>
      {right}
    </View>
  );
}

export function ToggleRow({
  title,
  subtitle,
  value,
  onValueChange,
  disabled,
}: {
  title: string;
  subtitle?: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  const p = usePalette();
  return (
    <Row
      title={title}
      subtitle={subtitle}
      disabled={disabled}
      right={
        <Switch
          value={value}
          onValueChange={onValueChange}
          disabled={disabled}
          trackColor={{ true: p.accent, false: p.line }}
          thumbColor="#FFFFFF"
          accessibilityLabel={title}
        />
      }
    />
  );
}

export function Divider() {
  return <View className="h-px bg-line dark:bg-line-dark" />;
}

/** Segmented control / option pills. */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  disabled,
}: {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
  disabled?: boolean;
}) {
  return (
    <View
      accessibilityRole="tablist"
      className={`flex-row rounded-2xl bg-line/60 p-1 dark:bg-line-dark ${disabled ? 'opacity-40' : ''}`}
    >
      {options.map((o) => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected, disabled }}
            disabled={disabled}
            onPress={() => onChange(o.value)}
            className={`flex-1 items-center rounded-xl py-2 ${
              selected ? 'bg-card shadow-sm dark:bg-card-dark' : ''
            }`}
          >
            <Text
              className={`text-sm font-semibold ${
                selected ? 'text-ink dark:text-ink-dark' : 'text-muted dark:text-muted-dark'
              }`}
            >
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}
