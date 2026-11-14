/**
 * Algoritmo de Bellman-Ford (version secuencial).
 *
 * Recorre la lista de aristas varias veces y relaja cada una.
 * Se detiene antes si en una pasada no cambia ninguna distancia
 * y al final hace una pasada extra para detectar ciclos negativos.
 */
public class BellmanFord {

    /** Valor que representa una distancia infinita (vertice no alcanzado). */
    public static final long INF = Long.MAX_VALUE / 4;

    /** Lo que devuelve el algoritmo: distancias y contadores. */
    public static class Resultado {
        public long[] distancias;
        public long relajaciones;     // veces que se evalua d[u] + w(u, v) < d[v]
        public int pasadas;           // pasadas sobre la lista de aristas (iteraciones)
        public boolean cicloNegativo; // true si hay un ciclo negativo alcanzable
    }

    /**
     * Ejecuta Bellman-Ford.
     *
     * @param vertices cantidad de vertices (numerados de 0 a vertices - 1)
     * @param aristas  arreglo de aristas; cada una es {u, v, peso}
     * @param origen   vertice de origen
     */
    public static Resultado ejecutar(int vertices, int[][] aristas, int origen) {

        // 1. Inicializar las distancias
        long[] dist = new long[vertices];
        java.util.Arrays.fill(dist, INF);
        dist[origen] = 0;

        long relajaciones = 0;
        int pasadas = 0;

        // 2. Relajar todas las aristas hasta V - 1 veces
        for (int i = 0; i < vertices - 1; i++) {
            boolean huboCambios = false;
            pasadas++;

            for (int[] a : aristas) {
                int u = a[0], v = a[1], peso = a[2];
                relajaciones++;
                // Si u no se ha alcanzado, no hay camino que mejorar
                if (dist[u] != INF && dist[u] + peso < dist[v]) {
                    dist[v] = dist[u] + peso;
                    huboCambios = true;
                }
            }

            // Deteccion anticipada: si nada cambio, ya son las distancias finales
            if (!huboCambios) {
                break;
            }
        }

        // 3. Pasada final para detectar ciclos negativos
        boolean cicloNegativo = false;
        for (int[] a : aristas) {
            int u = a[0], v = a[1], peso = a[2];
            relajaciones++;
            if (dist[u] != INF && dist[u] + peso < dist[v]) {
                cicloNegativo = true;
                break;
            }
        }

        Resultado r = new Resultado();
        r.distancias = dist;
        r.relajaciones = relajaciones;
        r.pasadas = pasadas;
        r.cicloNegativo = cicloNegativo;
        return r;
    }

    /** Prueba rapida del algoritmo. */
    public static void main(String[] args) {
        int[][] aristas = {
            {0, 1, 6}, {0, 2, 7}, {1, 3, 5}, {2, 3, 3}, {3, 4, 2}
        };
        Resultado r = ejecutar(5, aristas, 0);

        System.out.println("=== RESULTADOS BELLMAN-FORD ===");
        if (r.cicloNegativo) {
            System.out.println("Se detecto un ciclo negativo.");
        } else {
            for (int i = 0; i < r.distancias.length; i++) {
                System.out.println("Distancia al vertice " + i + ": " + r.distancias[i]);
            }
        }
        System.out.println("Relajaciones: " + r.relajaciones);
        System.out.println("Pasadas: " + r.pasadas);
    }
}
