#!/bin/bash

mkdir -p mymodule/info.kgeorgiy.ja.garipov.implementor/info/kgeorgiy/ja/garipov/implementor 2> /dev/null
cp -r ../java-solutions/info/kgeorgiy/ja/garipov/implementor mymodule/info.kgeorgiy.ja.garipov.implementor/info/kgeorgiy/ja/garipov
cp module-info.java mymodule/info.kgeorgiy.ja.garipov.implementor/module-info.java


mkdir src 2> /dev/null

javac   --module info.kgeorgiy.ja.garipov.implementor -d src --module-path ../../java-advanced-2021/lib/:../../java-advanced-2021/artifacts/ \
        --module-source-path ./mymodule

jar --create --file=Implementor.jar --manifest=MANIFEST.MF -C src/info.kgeorgiy.ja.garipov.implementor .

rm -r -f mymodule
rm -r -f src
