package test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import tda.BellmanFord;
import tda.DeltaStepping;

/*
 * Comparacion entre Bellman-Ford y Delta-Stepping.
 * 1. Genera los grafos de prueba (aleatorio y cuadricula).
 * 2. Hace las pruebas funcionales con grafos pequenos resueltos a mano.
 * 3. Ejecuta los experimentos E1 (tamano), E2 (densidad) y E3 (valor de delta),
 *    verifica que ambos algoritmos den las mismas distancias y guarda
 *    todas las mediciones en un archivo CSV.
 * Uso: sin argumentos -> experimentos completos
 *      argumento "rapido" -> version corta para probar que todo funciona
 */
public class Comparacion {
    // Parametros del estudio (secciones 4.1 a 4.5 del informe)
    static final int PESO_MINIMO = 1;
    static final int PESO_MAXIMO = 100;
    static final int[] SEMILLAS = {1, 2, 3};
    static final int CALENTAMIENTOS = 2;
    static int MEDICIONES = 10;
    static final double LIMITE_SEGUNDOS = 300;

    static int[] TAMANOS = {1000, 2000, 5000, 10000, 20000, 50000};  // E1
    static final int[] GRADOS = {2, 4, 8, 16, 32};                   // E2
    static final long INF_D = DeltaStepping.DELTA_INFINITO;
    static final long[] DELTAS = {1, 2, 5, 10, 25, 50, 100, INF_D};  // E3
    static int V_FIJO = 10000;                                       // E2 y E3

    /*
     * Grafo: cantidad de vertices y lista de aristas {u, v, peso}.
     */
    static class Grafo {
        // Atributos
        int vertices;
        int[][] aristas;
        int grado; // grado promedio nominal (4 en la cuadricula)

        // Crear
        Grafo(int vertices, int[][] aristas, int grado) {
            this.vertices = vertices;
            this.aristas = aristas;
            this.grado = grado;
        }
    }

    // ------------------------------------------------------------------
    // 1. Generacion de grafos (seccion 4.2)
    // ------------------------------------------------------------------

    /*
     * Grafo dirigido aleatorio: un ciclo por todos los vertices
     * mas aristas al azar hasta tener E = d * V.
     */
    static Grafo generarAleatorio(int vertices, int grado, long semilla) {
        Random rng = new Random(semilla);
        int totalAristas = grado * vertices;
        HashSet<Long> conexiones = new HashSet<Long>();
        long[] claves = new long[totalAristas]; // cada arista (u, v) se guarda como u * V + v
        int cantidad = 0;

        // El ciclo garantiza que todos los vertices sean alcanzables desde 0
        for (int u = 0; u < vertices; u++) {
            int v = (u + 1) % vertices;
            long clave = (long) u * vertices + v;
            conexiones.add(clave);
            claves[cantidad] = clave;
            cantidad++;
        }
        // Aristas al azar, sin lazos ni repetidas
        while (cantidad < totalAristas) {
            int u = rng.nextInt(vertices);
            int v = rng.nextInt(vertices);
            long clave = (long) u * vertices + v;
            if (u != v && !conexiones.contains(clave)) {
                conexiones.add(clave);
                claves[cantidad] = clave;
                cantidad++;
            }
        }
        // Ordenar por origen y destino, y asignar pesos enteros de 1 a 100
        Arrays.sort(claves);
        int[][] aristas = new int[totalAristas][3];
        for (int i = 0; i < totalAristas; i++) {
            aristas[i][0] = (int) (claves[i] / vertices);
            aristas[i][1] = (int) (claves[i] % vertices);
            aristas[i][2] = PESO_MINIMO + rng.nextInt(PESO_MAXIMO - PESO_MINIMO + 1);
        }
        return new Grafo(vertices, aristas, grado);
    }

    /*
     * Cuadricula de lado x lado, con aristas en ambos sentidos entre vecinos.
     */
    static Grafo generarCuadricula(int verticesSolicitados, long semilla) {
        Random rng = new Random(semilla);
        int lado = (int) Math.round(Math.sqrt(verticesSolicitados));
        int vertices = lado * lado;
        int totalAristas = 4 * lado * (lado - 1);
        int[][] aristas = new int[totalAristas][3];
        int k = 0;

        for (int fila = 0; fila < lado; fila++) {
            for (int col = 0; col < lado; col++) {
                int u = fila * lado + col;
                if (col + 1 < lado) {           // vecino de la derecha
                    aristas[k][0] = u;      aristas[k][1] = u + 1; k++;
                    aristas[k][0] = u + 1;  aristas[k][1] = u;     k++;
                }
                if (fila + 1 < lado) {          // vecino de abajo
                    aristas[k][0] = u;        aristas[k][1] = u + lado; k++;
                    aristas[k][0] = u + lado; aristas[k][1] = u;        k++;
                }
            }
        }
        for (int i = 0; i < totalAristas; i++) {
            aristas[i][2] = PESO_MINIMO + rng.nextInt(PESO_MAXIMO - PESO_MINIMO + 1);
        }
        return new Grafo(vertices, aristas, 4);
    }

    // Delta de referencia: max(1, piso(100 / d))  (seccion 4.3)
    static long deltaReferencia(int grado) {
        return Math.max(1, PESO_MAXIMO / grado);
    }

    static String textoDelta(long d) {
        if (d == INF_D) {
            return "inf";
        }
        return Long.toString(d);
    }

    // ------------------------------------------------------------------
    // 2. Pruebas funcionales (seccion 4.4)
    // ------------------------------------------------------------------
    static int pruebas = 0;

    static void comprobar(String nombre, boolean ok) {
        pruebas++;
        if (ok) {
            System.out.println("  OK    " + nombre);
        } else {
            System.out.println("  FALLA " + nombre);
            throw new RuntimeException("Error: fallo la prueba " + nombre);
        }
    }

    /*
     * Prueba un grafo con pesos no negativos: Bellman-Ford y Delta-Stepping
     * (delta = 1, 5 e infinito) deben dar las distancias calculadas a mano.
     */
    static void probarCaso(String nombre, int n, int[][] aristas, long[] esperado) {
        BellmanFord.Resultado bf = BellmanFord.ejecutar(n, aristas, 0);
        comprobar("Bellman-Ford, " + nombre,
                !bf.hayCicloNegativo() && Arrays.equals(bf.recuperarDistancias(), esperado));

        long[] deltasPrueba = {1, 5, INF_D};
        for (long d : deltasPrueba) {
            DeltaStepping.Resultado ds = DeltaStepping.ejecutar(n, aristas, 0, d);
            comprobar("Delta-Stepping (delta = " + textoDelta(d) + "), " + nombre,
                    Arrays.equals(ds.recuperarDistancias(), esperado));
        }
    }

    static void pruebasFuncionales() {
        final long X = BellmanFord.INF; // vertice inalcanzable
        System.out.println("=== PRUEBAS FUNCIONALES ===");

        probarCaso("G1 camino indirecto mas corto", 5,
                new int[][]{{0, 1, 4}, {0, 2, 2}, {2, 1, 1}, {1, 3, 5}, {2, 3, 8}},
                new long[]{0, 3, 2, 8, X});
        probarCaso("G2 aristas pesadas", 4,
                new int[][]{{0, 1, 15}, {0, 2, 2}, {2, 1, 3}, {1, 3, 4}, {2, 3, 20}},
                new long[]{0, 5, 2, 9});
        probarCaso("G3 vertices inalcanzables", 5,
                new int[][]{{0, 1, 3}, {1, 2, 5}, {3, 4, 2}},
                new long[]{0, 3, 8, X, X});
        probarCaso("G4 ciclo de pesos positivos", 3,
                new int[][]{{0, 1, 1}, {1, 2, 1}, {2, 0, 1}, {0, 2, 5}},
                new long[]{0, 1, 2});
        probarCaso("G5 varios caminos posibles", 5,
                new int[][]{{0, 1, 2}, {0, 2, 6}, {1, 2, 3}, {1, 3, 7}, {2, 3, 1}, {3, 4, 4}, {2, 4, 9}},
                new long[]{0, 2, 5, 6, 10});

        // Solo Bellman-Ford: pesos negativos sin ciclo negativo
        int[][] negativos = {{0, 1, 4}, {0, 2, 5}, {2, 1, -3}, {1, 3, 2}};
        BellmanFord.Resultado bf = BellmanFord.ejecutar(4, negativos, 0);
        comprobar("Bellman-Ford, G6 pesos negativos",
                !bf.hayCicloNegativo() && Arrays.equals(bf.recuperarDistancias(), new long[]{0, 2, 5, 4}));

        // Solo Bellman-Ford: ciclo negativo 1 -> 2 -> 1 (peso total -1)
        int[][] ciclo = {{0, 1, 1}, {1, 2, -2}, {2, 1, 1}};
        bf = BellmanFord.ejecutar(3, ciclo, 0);
        comprobar("Bellman-Ford, G7 ciclo negativo detectado", bf.hayCicloNegativo());

        System.out.println("Pruebas superadas: " + pruebas + " de " + pruebas + "\n");
    }

    // ------------------------------------------------------------------
    // 3. Experimentos, validacion y medicion (secciones 4.5 a 4.7)
    // ------------------------------------------------------------------
    static PrintWriter csv;
    static HashSet<String> combinacionesValidadas = new HashSet<String>();

    /*
     * validar: comprueba que Delta-Stepping da las mismas distancias que
     * Bellman-Ford (seccion 4.6).
     */
    static void validar(String familia, Grafo g, int semilla, long delta, long[] referencia) {
        DeltaStepping.Resultado ds = DeltaStepping.ejecutar(g.vertices, g.aristas, 0, delta);
        if (!Arrays.equals(ds.recuperarDistancias(), referencia)) {
            throw new RuntimeException("Error: las distancias no coinciden en " + familia
                    + " V=" + g.vertices + " d=" + g.grado + " delta=" + textoDelta(delta)
                    + " semilla=" + semilla);
        }
        combinacionesValidadas.add(familia + "|" + g.vertices + "|" + g.grado + "|"
                + textoDelta(delta) + "|" + semilla);
    }

    /*
     * medir: 2 calentamientos sin medir y 10 mediciones; cada medicion
     * se escribe en el CSV.
     */
    static void medir(String experimento, String familia, Grafo g, int semilla, String algoritmo, long delta) {
        for (int i = 0; i < CALENTAMIENTOS; i++) {
            if (correr(g, algoritmo, delta)[0] > LIMITE_SEGUNDOS) {
                escribir(experimento, familia, g, semilla, algoritmo, delta, 0, -1, -1, -1);
                System.out.println("    " + algoritmo + ": no termino en " + LIMITE_SEGUNDOS + " s");
                return;
            }
        }
        double suma = 0;
        for (int rep = 1; rep <= MEDICIONES; rep++) {
            double[] r = correr(g, algoritmo, delta);
            escribir(experimento, familia, g, semilla, algoritmo, delta, rep, r[0], (long) r[1], (long) r[2]);
            suma = suma + r[0];
            if (r[0] > LIMITE_SEGUNDOS) {
                System.out.println("    " + algoritmo + ": supero " + LIMITE_SEGUNDOS + " s, no se repite");
                return;
            }
        }
        String textoD = "-";
        if (algoritmo.equals("DeltaStepping")) {
            textoD = textoDelta(delta);
        }
        // Locale.US para que los decimales salgan con punto
        System.out.printf(Locale.US, "    %-14s delta=%-4s tiempo medio = %.4f s%n",
                algoritmo, textoD, suma / MEDICIONES);
    }

    /*
     * correr: una ejecucion; devuelve {segundos, relajaciones, iteraciones}.
     * Solo se mide la llamada al algoritmo.
     */
    static double[] correr(Grafo g, String algoritmo, long delta) {
        if (algoritmo.equals("BellmanFord")) {
            long t0 = System.nanoTime();
            BellmanFord.Resultado r = BellmanFord.ejecutar(g.vertices, g.aristas, 0);
            long t1 = System.nanoTime();
            return new double[]{(t1 - t0) / 1e9, r.recuperarRelajaciones(), r.recuperarPasadas()};
        } else {
            long t0 = System.nanoTime();
            DeltaStepping.Resultado r = DeltaStepping.ejecutar(g.vertices, g.aristas, 0, delta);
            long t1 = System.nanoTime();
            return new double[]{(t1 - t0) / 1e9, r.recuperarRelajaciones(), r.recuperarCubetasProcesadas()};
        }
    }

    // Escribe una fila en el CSV (Locale.US para que los decimales salgan con punto)
    static void escribir(String experimento, String familia, Grafo g, int semilla, String algoritmo,
                         long delta, int rep, double segundos, long relajaciones, long iteraciones) {
        String textoD = "";
        if (algoritmo.equals("DeltaStepping")) {
            textoD = textoDelta(delta);
        }
        csv.printf(Locale.US, "%s,%s,%d,%d,%d,%s,%d,%s,%d,%.6f,%d,%d%n",
                experimento, familia, g.vertices, g.aristas.length, g.grado, textoD,
                semilla, algoritmo, rep, segundos, relajaciones, iteraciones);
        csv.flush();
    }

    /*
     * correrGrafo: valida todos los delta indicados y luego mide ambos algoritmos.
     */
    static void correrGrafo(String experimento, String familia, Grafo g, int semilla, long[] deltas) {
        System.out.println("  " + experimento + " " + familia + " V=" + g.vertices
                + " E=" + g.aristas.length + " semilla=" + semilla);
        long[] referencia = BellmanFord.ejecutar(g.vertices, g.aristas, 0).recuperarDistancias();
        for (long d : deltas) {
            validar(familia, g, semilla, d, referencia);
        }
        medir(experimento, familia, g, semilla, "BellmanFord", 0); // no depende de delta
        for (long d : deltas) {
            medir(experimento, familia, g, semilla, "DeltaStepping", d);
        }
    }

    public static void main(String[] args) throws IOException {
        boolean rapido = args.length > 0 && args[0].equalsIgnoreCase("rapido");
        if (rapido) { // version corta para comprobar que todo funciona
            TAMANOS = new int[]{1000, 2000, 5000};
            V_FIJO = 2000;
            MEDICIONES = 3;
        }

        pruebasFuncionales();

        String archivo = "resultados.csv";
        if (rapido) {
            archivo = "resultados_rapido.csv";
        }
        csv = new PrintWriter(new FileWriter(archivo));
        csv.println("experimento,familia,vertices,aristas,grado,delta,semilla,algoritmo,"
                + "repeticion,tiempo_s,relajaciones,iteraciones");
        long inicio = System.nanoTime();

        // E1: varia el tamano V (aleatorio con d = 8 y cuadricula)
        System.out.println("=== E1: TAMANO ===");
        for (int v : TAMANOS) {
            for (int s : SEMILLAS) {
                correrGrafo("E1", "aleatorio", generarAleatorio(v, 8, s), s, new long[]{deltaReferencia(8)});
                correrGrafo("E1", "cuadricula", generarCuadricula(v, s), s, new long[]{deltaReferencia(4)});
            }
        }

        // E2: varia el grado promedio d (aleatorio, V fijo)
        System.out.println("=== E2: DENSIDAD ===");
        for (int d : GRADOS) {
            for (int s : SEMILLAS) {
                correrGrafo("E2", "aleatorio", generarAleatorio(V_FIJO, d, s), s, new long[]{deltaReferencia(d)});
            }
        }

        // E3: varia delta (aleatorio con d = 8 y cuadricula, V fijo)
        System.out.println("=== E3: VALOR DE DELTA ===");
        for (int s : SEMILLAS) {
            correrGrafo("E3", "aleatorio", generarAleatorio(V_FIJO, 8, s), s, DELTAS);
            correrGrafo("E3", "cuadricula", generarCuadricula(V_FIJO, s), s, DELTAS);
        }

        csv.close();
        System.out.println("\nCombinaciones validadas (grafo y delta distintos): "
                + combinacionesValidadas.size() + ", todas con las mismas distancias.");
        System.out.printf(Locale.US, "Tiempo total: %.1f min. Resultados en %s%n",
                (System.nanoTime() - inicio) / 6e10, archivo);
    }
}
