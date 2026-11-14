import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Comparacion entre Bellman-Ford y Delta-Stepping.
 *
 * 1. Genera los grafos de prueba (aleatorio y cuadricula).
 * 2. Hace las pruebas funcionales con grafos pequenos resueltos a mano.
 * 3. Ejecuta los experimentos E1 (tamano), E2 (densidad) y E3 (valor de delta),
 *    verifica que ambos algoritmos den las mismas distancias y guarda
 *    todas las mediciones en un archivo CSV.
 *
 * Uso:  java Comparacion            -> experimentos completos
 *       java Comparacion rapido     -> version corta para probar que todo funciona
 */
public class Comparacion {

    // ------------------------------------------------------------------
    // Parametros del estudio (secciones 4.1 a 4.5 del informe)
    // ------------------------------------------------------------------
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

    /** Un grafo: cantidad de vertices y lista de aristas {u, v, peso}. */
    static class Grafo {
        int vertices;
        int[][] aristas;
        int grado; // grado promedio nominal (4 en la cuadricula)
        Grafo(int vertices, int[][] aristas, int grado) {
            this.vertices = vertices;
            this.aristas = aristas;
            this.grado = grado;
        }
    }

    // ------------------------------------------------------------------
    // 1. Generacion de grafos (seccion 4.2)
    // ------------------------------------------------------------------

    /** Grafo dirigido aleatorio: un ciclo por todos los vertices + aristas al azar hasta E = d * V. */
    static Grafo generarAleatorio(int vertices, int grado, long semilla) {
        Random rng = new Random(semilla);
        long totalAristas = (long) grado * vertices;
        Set<Long> conexiones = new HashSet<>();
        List<int[]> lista = new ArrayList<>();

        // El ciclo garantiza que todos los vertices sean alcanzables desde 0
        for (int u = 0; u < vertices; u++) {
            int v = (u + 1) % vertices;
            conexiones.add((long) u * vertices + v);
            lista.add(new int[]{u, v, 0});
        }
        // Aristas al azar, sin lazos ni repetidas
        while (lista.size() < totalAristas) {
            int u = rng.nextInt(vertices);
            int v = rng.nextInt(vertices);
            if (u != v && conexiones.add((long) u * vertices + v)) {
                lista.add(new int[]{u, v, 0});
            }
        }
        // Ordenar por origen y destino, y asignar pesos enteros de 1 a 100
        lista.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        for (int[] a : lista) {
            a[2] = PESO_MINIMO + rng.nextInt(PESO_MAXIMO - PESO_MINIMO + 1);
        }
        return new Grafo(vertices, lista.toArray(new int[0][]), grado);
    }

    /** Cuadricula de lado x lado, con aristas en ambos sentidos entre vecinos. */
    static Grafo generarCuadricula(int verticesSolicitados, long semilla) {
        Random rng = new Random(semilla);
        int lado = (int) Math.round(Math.sqrt(verticesSolicitados));
        int vertices = lado * lado;
        List<int[]> lista = new ArrayList<>();

        for (int fila = 0; fila < lado; fila++) {
            for (int col = 0; col < lado; col++) {
                int u = fila * lado + col;
                if (col + 1 < lado) {           // vecino de la derecha
                    lista.add(new int[]{u, u + 1, 0});
                    lista.add(new int[]{u + 1, u, 0});
                }
                if (fila + 1 < lado) {          // vecino de abajo
                    lista.add(new int[]{u, u + lado, 0});
                    lista.add(new int[]{u + lado, u, 0});
                }
            }
        }
        for (int[] a : lista) {
            a[2] = PESO_MINIMO + rng.nextInt(PESO_MAXIMO - PESO_MINIMO + 1);
        }
        return new Grafo(vertices, lista.toArray(new int[0][]), 4);
    }

    /** Delta de referencia: max(1, floor(100 / d))  (seccion 4.3). */
    static long deltaReferencia(int grado) {
        return Math.max(1, PESO_MAXIMO / grado);
    }

    // ------------------------------------------------------------------
    // 2. Pruebas funcionales (seccion 4.4)
    // ------------------------------------------------------------------
    static int pruebas = 0;

    static void comprobar(String nombre, boolean ok) {
        pruebas++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + nombre);
        if (!ok) {
            throw new IllegalStateException("Fallo la prueba: " + nombre);
        }
    }

    static void pruebasFuncionales() {
        final long X = BellmanFord.INF; // vertice inalcanzable
        System.out.println("=== PRUEBAS FUNCIONALES ===");

        // Grafos con pesos no negativos y su solucion calculada a mano
        Object[][] casos = {
            {"G1 camino indirecto mas corto", 5,
                new int[][]{{0, 1, 4}, {0, 2, 2}, {2, 1, 1}, {1, 3, 5}, {2, 3, 8}},
                new long[]{0, 3, 2, 8, X}},
            {"G2 aristas pesadas", 4,
                new int[][]{{0, 1, 15}, {0, 2, 2}, {2, 1, 3}, {1, 3, 4}, {2, 3, 20}},
                new long[]{0, 5, 2, 9}},
            {"G3 vertices inalcanzables", 5,
                new int[][]{{0, 1, 3}, {1, 2, 5}, {3, 4, 2}},
                new long[]{0, 3, 8, X, X}},
            {"G4 ciclo de pesos positivos", 3,
                new int[][]{{0, 1, 1}, {1, 2, 1}, {2, 0, 1}, {0, 2, 5}},
                new long[]{0, 1, 2}},
            {"G5 varios caminos posibles", 5,
                new int[][]{{0, 1, 2}, {0, 2, 6}, {1, 2, 3}, {1, 3, 7}, {2, 3, 1}, {3, 4, 4}, {2, 4, 9}},
                new long[]{0, 2, 5, 6, 10}},
        };
        long[] deltasPrueba = {1, 5, INF_D};

        for (Object[] c : casos) {
            String nombre = (String) c[0];
            int n = (Integer) c[1];
            int[][] aristas = (int[][]) c[2];
            long[] esperado = (long[]) c[3];

            BellmanFord.Resultado bf = BellmanFord.ejecutar(n, aristas, 0);
            comprobar("Bellman-Ford, " + nombre,
                    !bf.cicloNegativo && Arrays.equals(bf.distancias, esperado));

            for (long d : deltasPrueba) {
                DeltaStepping.Resultado ds = DeltaStepping.ejecutar(n, aristas, 0, d);
                comprobar("Delta-Stepping (delta = " + textoDelta(d) + "), " + nombre,
                        Arrays.equals(ds.distancias, esperado));
            }
        }

        // Solo Bellman-Ford: pesos negativos sin ciclo negativo
        int[][] negativos = {{0, 1, 4}, {0, 2, 5}, {2, 1, -3}, {1, 3, 2}};
        BellmanFord.Resultado bf = BellmanFord.ejecutar(4, negativos, 0);
        comprobar("Bellman-Ford, G6 pesos negativos",
                !bf.cicloNegativo && Arrays.equals(bf.distancias, new long[]{0, 2, 5, 4}));

        // Solo Bellman-Ford: ciclo negativo 1 -> 2 -> 1 (peso total -1)
        int[][] ciclo = {{0, 1, 1}, {1, 2, -2}, {2, 1, 1}};
        bf = BellmanFord.ejecutar(3, ciclo, 0);
        comprobar("Bellman-Ford, G7 ciclo negativo detectado", bf.cicloNegativo);

        System.out.println("Pruebas superadas: " + pruebas + " de " + pruebas + "\n");
    }

    // ------------------------------------------------------------------
    // 3. Experimentos, validacion y medicion (secciones 4.5 a 4.7)
    // ------------------------------------------------------------------
    static PrintWriter csv;
    static Set<String> combinacionesValidadas = new HashSet<>();

    static String textoDelta(long d) {
        return d == INF_D ? "inf" : Long.toString(d);
    }

    /** Comprueba que Delta-Stepping da las mismas distancias que Bellman-Ford (seccion 4.6). */
    static void validar(String familia, Grafo g, int semilla, long delta, long[] referencia) {
        DeltaStepping.Resultado ds = DeltaStepping.ejecutar(g.vertices, g.aristas, 0, delta);
        if (!Arrays.equals(ds.distancias, referencia)) {
            throw new IllegalStateException("Las distancias no coinciden: " + familia
                    + " V=" + g.vertices + " d=" + g.grado + " delta=" + textoDelta(delta)
                    + " semilla=" + semilla);
        }
        combinacionesValidadas.add(familia + "|" + g.vertices + "|" + g.grado + "|"
                + textoDelta(delta) + "|" + semilla);
    }

    /** Ejecuta un algoritmo: 2 calentamientos sin medir y 10 mediciones; escribe cada una en el CSV. */
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
            suma += r[0];
            if (r[0] > LIMITE_SEGUNDOS) {
                System.out.println("    " + algoritmo + ": supero " + LIMITE_SEGUNDOS + " s, no se repite");
                return;
            }
        }
        System.out.printf(Locale.US, "    %-14s delta=%-4s tiempo medio = %.4f s%n",
                algoritmo, algoritmo.equals("BellmanFord") ? "-" : textoDelta(delta), suma / MEDICIONES);
    }

    /** Una ejecucion: devuelve {segundos, relajaciones, iteraciones}. Solo se mide la llamada al algoritmo. */
    static double[] correr(Grafo g, String algoritmo, long delta) {
        if (algoritmo.equals("BellmanFord")) {
            long t0 = System.nanoTime();
            BellmanFord.Resultado r = BellmanFord.ejecutar(g.vertices, g.aristas, 0);
            long t1 = System.nanoTime();
            return new double[]{(t1 - t0) / 1e9, r.relajaciones, r.pasadas};
        } else {
            long t0 = System.nanoTime();
            DeltaStepping.Resultado r = DeltaStepping.ejecutar(g.vertices, g.aristas, 0, delta);
            long t1 = System.nanoTime();
            return new double[]{(t1 - t0) / 1e9, r.relajaciones, r.cubetasProcesadas};
        }
    }

    static void escribir(String experimento, String familia, Grafo g, int semilla, String algoritmo,
                         long delta, int rep, double segundos, long relajaciones, long iteraciones) {
        csv.printf(Locale.US, "%s,%s,%d,%d,%d,%s,%d,%s,%d,%.6f,%d,%d%n",
                experimento, familia, g.vertices, g.aristas.length, g.grado,
                algoritmo.equals("BellmanFord") ? "" : textoDelta(delta),
                semilla, algoritmo, rep, segundos, relajaciones, iteraciones);
        csv.flush();
    }

    /** Corre un grafo: valida todos los delta indicados y luego mide ambos algoritmos. */
    static void correrGrafo(String experimento, String familia, Grafo g, int semilla, long[] deltas) {
        System.out.println("  " + experimento + " " + familia + " V=" + g.vertices
                + " E=" + g.aristas.length + " semilla=" + semilla);
        long[] referencia = BellmanFord.ejecutar(g.vertices, g.aristas, 0).distancias;
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

        String archivo = rapido ? "resultados_rapido.csv" : "resultados.csv";
        csv = new PrintWriter(new FileWriter(archivo));
        csv.println("experimento,familia,vertices,aristas,grado,delta,semilla,algoritmo,"
                + "repeticion,tiempo_s,relajaciones,iteraciones");
        long inicio = System.nanoTime();

        // E1: varia el tamano V (aleatorio con d = 8 y cuadricula)
        System.out.println("=== E1: TAMANO ===");
        for (int v : TAMANOS) {
            for (int s : SEMILLAS) {
                Grafo a = generarAleatorio(v, 8, s);
                correrGrafo("E1", "aleatorio", a, s, new long[]{deltaReferencia(8)});
                Grafo c = generarCuadricula(v, s);
                correrGrafo("E1", "cuadricula", c, s, new long[]{deltaReferencia(4)});
            }
        }

        // E2: varia el grado promedio d (aleatorio, V fijo)
        System.out.println("=== E2: DENSIDAD ===");
        for (int d : GRADOS) {
            for (int s : SEMILLAS) {
                Grafo a = generarAleatorio(V_FIJO, d, s);
                correrGrafo("E2", "aleatorio", a, s, new long[]{deltaReferencia(d)});
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
