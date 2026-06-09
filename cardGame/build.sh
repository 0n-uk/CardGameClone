#!/bin/bash
set -e

SRC=src
RESOURCES=resources
OUT=target/classes
JAR=target/cardgame.jar
MAIN=cardGame.main

echo "Cleaning previous build..."
rm -rf target
mkdir -p "$OUT"

echo "Compiling sources..."
find "$SRC" -name "*.java" > sources.txt
javac -source 17 -target 17 -encoding UTF-8 -d "$OUT" @sources.txt
rm sources.txt

echo "Copying resources..."
cp -r "$RESOURCES"/. "$OUT"/

echo "Building JAR..."
echo "Main-Class: $MAIN" > manifest.txt
jar cfm "$JAR" manifest.txt -C "$OUT" .
rm manifest.txt

echo ""
echo "Done! Run with:"
echo "  java -jar $JAR"
