
mkdir -p mymodule\info.kgeorgiy.ja.garipov.implementor\info\kgeorgiy\ja\garipov\implementor
cp -r ..\java-solutions\info\kgeorgiy\ja\garipov\implementor mymodule\info.kgeorgiy.ja.garipov.implementor\info\kgeorgiy\ja\garipov
cp module-info.java mymodule\info.kgeorgiy.ja.garipov.implementor\module-info.java

set scripts_dir=%CD%

cd ..\..\
"C:\Program Files\Java\jdk-11.0.9\bin\javadoc.exe"	 \
    -link https://docs.oracle.com/en/java/javase/11/docs/api/ ^
    -private ^
    -d %scripts_dir%\docs ^
    -cp java-advanced-2021\artifacts\info.kgeorgiy.java.advanced.implementor.jar: ^
    $scripts_dir\mymodule\info.kgeorgiy.ja.garipov.implementor\info\kgeorgiy\ja\garipov\implementor\Implementor.java ^
    java-advanced-2021\modules\info.kgeorgiy.java.advanced.implementor\info\kgeorgiy\java\advanced\implementor\ImplerException.java ^
    java-advanced-2021\modules\info.kgeorgiy.java.advanced.implementor\info\kgeorgiy\java\advanced\implementor\JarImpler.java ^
    java-advanced-2021\modules\info.kgeorgiy.java.advanced.implementor\info\kgeorgiy\java\advanced\implementor\Impler.java

rm -r -f %scripts_dir%\mymodule
 
