# Model loader tests

Needs Python 3 with Pillow, and a JDK 21. From this folder:

    mkdir -p assets/obj assets/glb test out
    python3 gen.py
    cp T.java test/T.java
    javac -d out ../../src/main/java/com/cubecraft/models/*.java test/T.java
    java -cp out T

Prints PASS / FAIL per check and ends with ALL PASSED.
