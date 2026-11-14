package tda;

/*
 * Algoritmo de Bellman-Ford (version secuencial).
 * Recorre la lista de aristas varias veces y relaja cada una.
 * Se detiene antes si en una pasada no cambia ninguna distancia
 * y al final hace una pasada extra para detectar ciclos negativos.
 */
public class BellmanFord {
    // Valor que representa una distancia infinita (vertice no alcanzado)
    public static final long INF = Long.MAX_VALUE / 4;

    /*
     * Resultado: lo que devuelve el algoritmo (distancias y contadores).
     */
    public static class Resultado {
        // Atributos
        private long[] distancias;
        private long relajaciones;     // veces que se evalua d[u] + w(u, v) < d[v]
        private int pasadas;           // pasadas sobre la lista de aristas (iteraciones)
        private boolean cicloNegativo; // true si hay un ciclo negativo alcanzable

        // Metodos
        public Resultado(long[] distancias, long relajaciones, int pasadas, boolean cicloNegativo) {
            this.distancias = distancias;
            this.relajaciones = relajaciones;
            this.pasadas = pasadas;
            this.cicloNegativo = cicloNegativo;
        }
        public long[] recuperarDistancias() {
            return distancias;
        }
        public long recuperarRelajaciones() {
            return relajaciones;
        }
        public int recuperarPasadas() {
            return pasadas;
        }
        public boolean hayCicloNegativo() {
            return cicloNegativo;
        }
    }

    /*
     * ejecutar(vertices, aristas, origen): aplica Bellman-Ford.
     * vertices: cantidad de vertices (numerados de 0 a vertices - 1)
     * aristas: arreglo de aristas; cada una es {u, v, peso}
     * origen: vertice de origen
     */
    public static Resultado ejecutar(int vertices, int[][] aristas, int origen) {
        // 1. Inicializar las distancias
        long[] dist = new long[vertices];
        for (int i = 0; i < vertices; i++) {
            dist[i] = INF;
        }
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

            // Detencion anticipada: si nada cambio, ya son las distancias finales
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

        return new Resultado(dist, relajaciones, pasadas, cicloNegativo);
    }
}
