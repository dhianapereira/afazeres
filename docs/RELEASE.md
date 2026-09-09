# Processo de release

As releases do Afazeres são criadas automaticamente quando uma tag Semantic Versioning no formato `vMAJOR.MINOR.PATCH` é enviada ao GitHub. O workflow executa lint e testes, gera APK e Android App Bundle (AAB) assinados, calcula checksums SHA-256 e publica uma GitHub Release com changelog gerado pelo GitHub.

## Configuração inicial (uma única vez)

### 1. Criar e guardar a chave de assinatura

Crie a chave fora do repositório:

```bash
keytool -genkeypair -v \
  -keystore afazeres-release.jks \
  -alias afazeres \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Faça ao menos dois backups seguros e separados do arquivo `.jks`, do alias e das senhas. Perder essa chave pode impedir atualizações da aplicação. Nunca faça commit da chave ou das senhas.

### 2. Configurar os GitHub Actions Secrets

Converta o keystore para Base64 em uma única linha:

```bash
base64 < afazeres-release.jks | tr -d '\n'
```

Em **Settings → Secrets and variables → Actions**, cadastre estes Repository Secrets:

| Secret                      | Conteúdo                                 |
| --------------------------- | ---------------------------------------- |
| `RELEASE_KEYSTORE_BASE64`   | Resultado do comando Base64              |
| `RELEASE_KEYSTORE_PASSWORD` | Senha do keystore                        |
| `RELEASE_KEY_ALIAS`         | Alias da chave, por exemplo `afazeres` |
| `RELEASE_KEY_PASSWORD`      | Senha da chave                           |

O workflow usa o `GITHUB_TOKEN` temporário do próprio job para publicar a release; nenhum Personal Access Token é necessário.

### 3. Proteger o repositório

Em **Settings → Actions → General**, mantenha a permissão padrão do workflow como **Read repository contents**. O job de release recebe `contents: write` explicitamente apenas durante a publicação.

Proteja a branch `main`: exija pull request, aprovação e o check de CI antes do merge. Ative Dependabot alerts, secret scanning e push protection quando estiverem disponíveis no plano do repositório.

## Gerar uma release

1. Garanta que tudo que entrará na versão esteja na `main` e que o CI esteja verde.
2. Escolha a versão seguindo [Semantic Versioning](https://semver.org/):
   - `PATCH` (`v1.0.1`): correção compatível;
   - `MINOR` (`v1.1.0`): funcionalidade compatível;
   - `MAJOR` (`v2.0.0`): mudança incompatível.
3. Atualize sua branch e revise o commit que será publicado:

```bash
git switch main
git pull --ff-only
git status
git log -1 --oneline
```

4. Crie uma tag anotada no commit aprovado e envie somente essa tag:

```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

5. Acompanhe **Actions → Android Release**. Após sucesso, confira em **Releases**:
   - changelog gerado;
   - `afazeres-VERSION.apk`;
   - `afazeres-VERSION.aab`;
   - `SHA256SUMS.txt`.

A tag determina automaticamente `versionName` e `versionCode`. Por exemplo, `v1.2.3` produz `versionName=1.2.3` e `versionCode=1002003`. Não é necessário alterar esses valores manualmente antes de cada release.

## Se o workflow falhar

Corrija o problema em um novo commit. Como uma tag publicada deve ser imutável, não reutilize a versão que falhou: crie a próxima versão (por exemplo, `v1.0.1`) e envie a nova tag.

Se uma tag foi criada localmente no commit errado, mas ainda **não foi enviada**, remova-a e recrie-a:

```bash
git tag -d v1.0.0
git tag -a v1.0.0 -m "Release v1.0.0"
```

Não apague nem mova tags já publicadas, exceto em uma emergência deliberadamente avaliada.

## Teste local de release

O build local sem as quatro variáveis de assinatura gera artefatos não assinados. Para validar assinatura localmente, defina temporariamente `RELEASE_VERSION_NAME`, `RELEASE_VERSION_CODE`, `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` e `RELEASE_KEY_PASSWORD`, então execute:

```bash
./gradlew lintRelease testDebugUnitTest bundleRelease assembleRelease
```

Evite salvar senhas no histórico do shell, em `gradle.properties`, `local.properties` ou `key.properties`.
