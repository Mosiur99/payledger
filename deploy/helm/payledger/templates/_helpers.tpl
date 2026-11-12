{{- define "payledger.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "payledger.labels" -}}
{{ include "payledger.selectorLabels" . }}
app.kubernetes.io/part-of: payledger
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "payledger.dbSecret" -}}
{{- .svc.database.existingSecret | default (printf "%s-db" .name) }}
{{- end }}
