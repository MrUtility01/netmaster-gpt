#!/usr/bin/env bash
set -euo pipefail

SCREENS=app/src/main/java/com/example/netmaster/ui/NetMasterScreens.kt
VM=app/src/main/java/com/example/netmaster/ui/NetMasterViewModel.kt
APP=app/src/main/java/com/example/netmaster/ui/NetMasterApp.kt

# NetMasterScreens: missing imports
if ! grep -q 'import androidx.compose.material.icons.Icons' "$SCREENS"; then
  sed -i 's|import androidx.compose.material3.\*|import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Terminal\nimport androidx.compose.material3.*|' "$SCREENS"
fi
if ! grep -q 'import androidx.compose.ui.text.font.FontFamily' "$SCREENS"; then
  sed -i 's|import androidx.compose.ui.text.font.FontWeight|import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextDirection|' "$SCREENS"
fi

# QuizScreen missing @Composable
if grep -q '^fun QuizScreen' "$SCREENS"; then
  sed -i 's/^fun QuizScreen/@Composable\nfun QuizScreen/' "$SCREENS"
fi

python3 - <<'PY'
from pathlib import Path

# ViewModel
p = Path('app/src/main/java/com/example/netmaster/ui/NetMasterViewModel.kt')
t = p.read_text(encoding='utf-8')
changed = False
old = '''    private fun contentKey(source: String): String? {
        when {
            source.startsWith("curriculum/") -> source.removePrefix("curriculum/").substringBefore('/').takeIf { it.isNotBlank() }?.let { "lesson:$it" }
            source.startsWith("reference/") -> source.removePrefix("reference/").takeIf { it.isNotBlank() }?.let { "ref:$it" }
            else -> null
        }
    }'''
new = '''    private fun contentKey(source: String): String? {
        return when {
            source.startsWith("curriculum/") -> source.removePrefix("curriculum/").substringBefore('/').takeIf { it.isNotBlank() }?.let { "lesson:$it" }
            source.startsWith("reference/") -> source.removePrefix("reference/").takeIf { it.isNotBlank() }?.let { "ref:$it" }
            else -> null
        }
    }'''
if old in t:
    t = t.replace(old, new, 1); changed = True; print('contentKey fixed')
old2 = '''            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()'''
new2 = '''            val pbe = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(pbe).encoded
            pbe.clearPassword()'''
if old2 in t:
    t = t.replace(old2, new2, 1); changed = True; print('clearPassword fixed')
if changed:
    p.write_text(t, encoding='utf-8')

# NetMasterApp OptIn
p = Path('app/src/main/java/com/example/netmaster/ui/NetMasterApp.kt')
t = p.read_text(encoding='utf-8')
if 'ExperimentalMaterial3Api' not in t:
    t = t.replace('package com.example.netmaster.ui\n', 'package com.example.netmaster.ui\n\nimport androidx.compose.material3.ExperimentalMaterial3Api\n', 1)
    t = t.replace('@Composable\nprivate fun MainAppShell', '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun MainAppShell', 1)
    p.write_text(t, encoding='utf-8'); print('OptIn added')

# LessonEnricher
p = Path('app/src/main/java/com/example/netmaster/domain/LessonEnricher.kt')
t = p.read_text(encoding='utf-8')
orig = t
t = t.replace('lesson.failureMatrix.ifEmpty { parseTroubleshoot(lesson.troubleshooting) }', 'lesson.failureMatrix.ifEmpty { lesson.troubleshooting }')
t = t.replace(
    'if (lesson.failureMatrix.isNotEmpty()) lesson.failureMatrix else if (isBoilerplate(lesson.troubleshooting, listOf("Failure Matrix"))) b.failures else parseTroubleshoot(lesson.troubleshooting)',
    'if (lesson.failureMatrix.isNotEmpty()) lesson.failureMatrix else if (lesson.troubleshooting.isNotEmpty()) lesson.troubleshooting else b.failures',
)
if t != orig:
    p.write_text(t, encoding='utf-8'); print('Enricher updated')

# DeepContentMapper: String -> List for troubleshooting
p = Path('app/src/main/java/com/example/netmaster/data/DeepContentMapper.kt')
t = p.read_text(encoding='utf-8')
orig = t
t = t.replace(
    'troubleshooting="${s.expectedFinding}\nRollback: ${s.rollback}",',
    'troubleshooting=listOf(FailureCase(s.symptom, s.hypotheses.firstOrNull().orEmpty(), s.expectedFinding, s.rollback)),',
)
# DeepLesson.toLesson: convert String troubleshooting field to list
t = t.replace(
    'lab=lab,troubleshooting=troubleshooting,questions=questions',
    'lab=lab,troubleshooting=if (troubleshooting.isBlank()) emptyList() else listOf(FailureCase(hypothesis=troubleshooting.take(200))),questions=questions',
)
if t != orig:
    p.write_text(t, encoding='utf-8'); print('DeepContentMapper updated')
else:
    print('DeepContentMapper patterns not found or already fixed')

# SearchEngine: List troubleshooting is not a String
p = Path('app/src/main/java/com/example/netmaster/search/SearchEngine.kt')
t = p.read_text(encoding='utf-8')
orig = t
t = t.replace(
    '"Troubleshooting" to l.troubleshooting,',
    '"Troubleshooting" to l.troubleshooting.joinToString(" ") { "${it.symptom} ${it.hypothesis} ${it.evidence} ${it.next}" },',
)
if t != orig:
    p.write_text(t, encoding='utf-8'); print('SearchEngine updated')
else:
    print('SearchEngine already fixed or pattern missing')

print('Compile fixes applied.')
PY
