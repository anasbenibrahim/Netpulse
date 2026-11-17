import { CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatTime } from '../lib/format'

export interface Series {
  key: string
  label: string
  color: string
}

interface Props<T> {
  data: T[]
  series: Series[]
  unit: string
  domain?: [number | string, number | string]
  /** Dashed line at an anomaly threshold. */
  threshold?: { value: number; label: string }
}

/** Line chart over receivedAt. Missing measurements (null) show as gaps, not as zeros. */
export function MetricChart<T extends { receivedAt: string }>({ data, series, unit, domain, threshold }: Props<T>) {
  return (
    <div className="h-56 w-full">
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -8 }}>
          <CartesianGrid stroke="#e2e8f0" vertical={false} />
          <XAxis
            dataKey="receivedAt"
            tickFormatter={formatTime}
            minTickGap={48}
            tick={{ fontSize: 11, fill: '#64748b' }}
            stroke="#cbd5e1"
          />
          <YAxis domain={domain ?? ['auto', 'auto']} tick={{ fontSize: 11, fill: '#64748b' }} stroke="#cbd5e1" width={56} />
          <Tooltip
            labelFormatter={(v) => formatTime(String(v))}
            formatter={(v) => (typeof v === 'number' ? `${v.toFixed(1)} ${unit}` : 'N/A')}
            contentStyle={{ fontSize: 12, borderRadius: 8 }}
          />
          {series.length > 1 && <Legend wrapperStyle={{ fontSize: 12 }} />}
          {threshold && (
            <ReferenceLine
              y={threshold.value}
              stroke="#f59e0b"
              strokeDasharray="4 4"
              label={{ value: threshold.label, fontSize: 10, fill: '#b45309', position: 'insideTopRight' }}
            />
          )}
          {series.map((s) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.label}
              stroke={s.color}
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
              connectNulls={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
