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

# ViewModel fixes via Python for multi-line safety
python3 - <<'PY'
from pathlib import Path
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
    t = t.replace(old, new, 1)
    changed = True
    print('contentKey fixed')
old2 = '''            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()'''
new2 = '''            val pbe = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(pbe).encoded
            pbe.clearPassword()'''
if old2 in t:
    t = t.replace(old2, new2, 1)
    changed = True
    print('clearPassword fixed')
if changed:
    p.write_text(t, encoding='utf-8')
PY

# OptIn for TopAppBar
if ! grep -q 'ExperimentalMaterial3Api' "$APP"; then
  # Add import after package line
  sed -i '0,/^package com.example.netmaster.ui$/s//package com.example.netmaster.ui\n\nimport androidx.compose.material3.ExperimentalMaterial3Api/' "$APP"
  # Add OptIn before private fun MainAppShell (keep @Composable above it)
  sed -i 's/^@Composable\nprivate fun MainAppShell/@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun MainAppShell/' "$APP" || true
  sed -i 's/^private fun MainAppShell/@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun MainAppShell/' "$APP" || true
fi

echo 'Compile fixes applied.'
