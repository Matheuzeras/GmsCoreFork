# microG GmsCore FaceFix PR #3899

Esta branch traz o código completo do [PR #3899](https://github.com/microg/GmsCore/pull/3899) para tratar as coordenadas e contornos de reconhecimento facial do ML Kit.

**Escopo:** microG (GmsCore), especialmente casos de crash na verificação facial do Mercado Pago e Mercado Livre; **não é garantido** que o bug seja eliminado nesses aplicativos. O PR não teve validação publicada em uma sessão real do Mercado Pago.

## Alterações
- FaceFrameMapping.kt: remapeia coordenadas de imagens rotacionadas para o frame original.
- FaceDetector.kt: aplica o mapeamento aos resultados dos caminhos bitmap, NV21 e YUV.
- FaceDetectorHelper.kt: regula limites de retângulos e quantidades de pontos dos contornos, protege entrada inválida e libera Mat.
- Barcode extensions: usa o mapeamento compartilhado.
- Testes de unidade para rotações 0, 90, 180 e 270 graus.

Código copiado de [rgbeans/GmsCore@b6e3b2c](https://github.com/rgbeans/GmsCore/commit/b6e3b2c041dcf9988e64844f880c6800d37fd093), branch do PR #3899. Os seis arquivos eram idênticos à base do PR no fork, exceto os dois arquivos novos. Outros módulos e workflows permanecem inalterados.

## Build experimental

Abra [Actions](../../actions/workflows/build-facefix-3899.yml) e selecione um run desta branch. A compilação publica somente os APKs `mapboxDefaultDebug` e `mapboxDefaultRelease` do GmsCore, em artefatos separados.

- **Debug**: assinado com chave de debug do ambiente GitHub Actions (incompatível, em geral, com a assinatura da ROM).
- **Release**: unsigned, precisa ser assinado por uma chave **legitimamente compatível** com o GmsCore instalado.
- **Nenhum dos dois** deve ser instalado por cima de um `com.google.android.gms` do sistema sem comparar certificados, package name e versionCode.

Para compilar localmente, use JDK 17, Android SDK/Build Tools e execute:
```sh
./gradlew :play-services-vision-core:testDebugUnitTest
./gradlew :play-services-core:assembleMapboxDefaultDebug :play-services-core:assembleMapboxDefaultRelease
```

No Android 16 (API 36), instalar como módulo KernelSU Next exige correspondência da assinatura com o APK de sistema e montagem systemless funcional via metamodule. Se não possuir a chave da ROM, não tente ignorar a validação de assinatura: considere outro mecanismo de patch com escopo controlado e testes locais.
