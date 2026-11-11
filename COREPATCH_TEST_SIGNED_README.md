# GmsCore FaceFix #3899 - release para teste CorePatch

O build de GmsCore com o PR #3899 gera **com.google.android.gms** (microG) versao **0.3.17.252432-facefix3899**, `versionCode=252432035`.

O workflow publica **GmsCore-FaceFix3899-CorePatch-TEST-SIGNED-Release**, um APK release assinado com uma chave de TESTE temporaria. Nao e uma assinatura NOGAPPS. A chave nao fica armazenada. CorePatch pode permitir a instalacao sobre a atualizacao do sistema **somente** se o modulo estiver operante no Android 16 e se nao houver outras verificacoes impeditivas; isso nao cria um certificado original. O novo APK pode afetar permissoes de assinatura e aplicativos dependentes do microG, incluindo apps financeiros.

## Procedimento controlado no Windows CMD

Antes de qualquer instalacao, mantenha um backup do APK original e seus dados fora do telefone.

- Deixe **desativado** o modulo experimental FaceFix LSPosed v0.2. Ele causou um crash adicional NPE em facevalidation.
- Nao remova o microG oficial do sistema. Nao apague dados do microG nem do Mercado Pago.
- Confirme CorePatch ligado no **Android System Framework**. Android 16 pode precisar de Core Patch N e suporte a libxposed API 101; CorePatch antigo nao necessariamente funciona.
- Extraia o artefato do GitHub e execute:

    adb install -r "GmsCore-FaceFix3899-CorePatch-TEST-SIGNED.apk"

- Se Android devolver INSTALL_FAILED_UPDATE_INCOMPATIBLE ou INSTALL_FAILED_DUPLICATE_PERMISSION, **nao desinstale microG** e nao tente comandos de alteracao de dados do sistema.
- Verifique o pacote ativo:

    adb shell dumpsys package com.google.android.gms | findstr /C:"codePath=" /C:"versionName=" /C:"versionCode="

- Antes de testar apps financeiros, confirme funcionalidade e estabilidade do microG. A instalacao nao garante que a verificacao facial funcione ou passe pelas exigencias de integridade dos apps.

## Importante: limite da verificacao de assinatura

O CorePatch nao torna o APK oficial, nao converte o certificado de teste em NOGAPPS e nao garante manter permissoes do tipo signature. Portanto, este pacote e para teste reversivel mediante restauracao apropriada; nao e uma atualizacao certificada.

Repositorio oficial CorePatch N: https://github.com/LSPosed/CorePatch
Patch microG original: https://github.com/microg/GmsCore/pull/3899
