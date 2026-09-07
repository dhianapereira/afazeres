# Processo de release

As releases do Afazeres são criadas quando uma tag Semantic Versioning no formato `vMAJOR.MINOR.PATCH` é enviada ao GitHub. O workflow valida a tag, executa lint e testes, gera APK e Android App Bundle (AAB) assinados, calcula checksums SHA-256 e publica uma GitHub Release.

## Configuração inicial

### 1. Criar a chave de assinatura

Crie a chave fora do repositório:

```bash
keytool -genkeypair -v \
  -keystore afazeres-release.jks \
  -alias afazeres \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Guarde ao menos dois backups seguros e separados da chave, alias e senhas. Nunca faça commit desses dados. Para publicação no Google Play, ative o Play App Signing e guarde esta chave como a upload key.

### 2. Configurar os GitHub Actions Secrets

Converta o keystore para Base64 em uma única linha:

```bash
base64 < afazeres-release.jks | tr -d '\n'
```

Em **Settings → Secrets and variables → Actions**, cadastre:

| Secret | Conteúdo |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Keystore codificado em Base64 |
| `RELEASE_KEYSTORE_PASSWORD` | Senha do keystore |
| `RELEASE_KEY_ALIAS` | Alias, por exemplo `afazeres` |
| `RELEASE_KEY_PASSWORD` | Senha da chave |

O workflow usa somente o `GITHUB_TOKEN` temporário para publicar a GitHub Release. Nenhum Personal Access Token é necessário.

### 3. Proteger o repositório

- Mantenha a permissão padrão dos workflows como somente leitura.
- Proteja a branch `main` exigindo pull request, aprovação e o check `Build, lint, and tests`.
- Ative Dependabot alerts, secret scanning e push protection.
- Restrinja quem pode criar tags de release.
- Não armazene credenciais em `gradle.properties`, `local.properties` ou `key.properties`.

## Criar uma release

Com o commit aprovado presente na `main`:

```bash
git switch main
git pull --ff-only
git status
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

A tag define automaticamente `versionName` e `versionCode`. Por exemplo, `v1.2.3` gera `versionName=1.2.3` e `versionCode=1002003`.

Após o workflow terminar, revise na GitHub Release:

- `afazeres-VERSION.aab` para envio ao Google Play Console;
- `afazeres-VERSION.apk` para testes e distribuição direta;
- `SHA256SUMS.txt` para verificação de integridade;
- changelog gerado automaticamente.

Uma tag publicada deve ser considerada imutável. Se uma release falhar depois do envio da tag, corrija em um novo commit e publique uma nova versão.

## Validação local

Sem as variáveis de assinatura, o Gradle gera artefatos de release não assinados. Para validar uma assinatura local, forneça temporariamente:

- `RELEASE_VERSION_NAME`
- `RELEASE_VERSION_CODE`
- `RELEASE_KEYSTORE_PATH`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Depois execute:

```bash
./gradlew lintRelease testDebugUnitTest bundleRelease assembleRelease
```
