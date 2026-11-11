# MicroG FaceFix v0.2 experimental (LSPosed / Vector)

**Compatibilidade em investigação; ainda nao testado no Mercado Pago.**

## Descoberta nos logs
O logcat de 11/11/2026 confirma FaceDetection no PID do com.mercadopago.wallet, seguido de SIGSEGV em libhwui.so / YuvImage.compressToJpeg quando o detector acha um rosto. A v0.1 so registrava hook para com.google.android.gms, deixando o processo real sem a correcao.

## Alteracoes
- Legacy Xposed, scope recomendado: com.mercadopago.wallet e com.mercadolibre.android.
- Intercepta FaceDetector do microG carregado no processo do app, inclusive carregamento por classloader remoto.
- Corrige coordenadas de retangulo/landmarks/contours apos rotacao, preservando as contagens originais e os dados de confianca. Nao altera verificacao de identidade ou app financeiro.
- Registra no logcat MicroGFaceFix: v0.2 in process, v0.2 hooked ML Kit detector, e v0.2 applied coordinate fix to face results (somente quando houver faces).
- Nao registra dados biometricos ou imagens.

## Como testar
1. Este arquivo e um APK LSPosed/Vector, nao um modulo ZIP do KernelSU.
2. Instale o APK mais recente; se o Android rejeitar a atualizacao devido a chave de debug efemera, desinstale APENAS o app dev.microg.facefix antigo, e nao o GmsCore.
3. Habilite o modulo no Vector/LSPosed e selecione explicitamente Mercado Pago e Mercado Livre (nao basta selecionar microG).
4. Reinicie o aparelho para recarregar o modulo.
5. Execute no Windows CMD: adb logcat -c
6. Reproduza uma falha uma vez.
7. Execute: adb logcat -d -v time | findstr /I /C:"MicroGFaceFix" /C:"FaceDetection" > facefix-v02.txt
8. Execute: adb logcat -b crash -v time -d > crash-v02.txt
9. Verifique se aparecem as tres mensagens na mesma tentativa. Registro do hook sozinho NAO prova execucao do mapeamento.

## Reversao
Desabilite o modulo pelo Vector/LSPosed e reinicie. Nao desinstale o microG nem altere o aplicativo Mercado Pago.

## Limites
Esta correcao nao equivale ao PR upstream #3899 completo; ela nao inclui liberacao de memoria Mat ou contornos aproximados de 36 pontos e pode nao resolver falhas nativas distintas. Hook em processos de apps financeiros pode disparar politicas de integridade; nenhum bypass esta incluido.
