mkdir -p mymodule\info.kgeorgiy.ja.garipov.implementor\info\kgeorgiy\ja\garipov\implementor
cp -r ..\java-solutions\info\kgeorgiy\ja\garipov\implementor mymodule\info.kgeorgiy.ja.garipov.implementor\info\kgeorgiy\ja\garipov
cp module-info.java mymodule\info.kgeorgiy.ja.garipov.implementor\module-info.java


mkdir src

"C:\Program Files\Java\jdk-11.0.9\bin\javac.exe"   --module info.kgeorgiy.ja.garipov.implementor -d src --module-path ..\..\java-advanced-2021\lib\:..\..\java-advanced-2021\artifacts\ ^
        --module-source-path .\mymodule

"C:\Program Files\Java\jdk-11.0.9\bin\jar.exe" --create --file=Implementor.jar --manifest=MANIFEST.MF -C src\info.kgeorgiy.ja.garipov.implementor .

rm -r -f mymodule
rm -r -f src
