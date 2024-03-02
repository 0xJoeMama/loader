# TODO: unhardcode this
CP=$(<./libs/1.20.4.classpath)

set -e

# Build
echo "[INFO] building the loader"
kotlinc -cp $CP:libs/loader_libs/asm.jar -include-runtime -d build/loader.jar io/github/joemama/loader/main.kt
echo "[INFO] loader was built"

# Run
echo "[INFO] running the loader"
java -cp $CP:build/loader.jar io.github.joemama.loader.MainKt libs/1.20.4.jar $@
echo "[INFO] loader has exited"

