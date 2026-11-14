package tda;

import java.util.ArrayList;
import java.util.TreeMap;

/*
 * Algoritmo Delta-Stepping (version secuencial).
 * Agrupa los vertices en cubetas de ancho delta segun su distancia tentativa.
 * Procesa siempre la primera cubeta no vacia: primero relaja las aristas
 * ligeras (peso <= delta) hasta que la cubeta quede vacia, y despues relaja
 * una vez las aristas pesadas (peso > delta) de los vertices procesados.
 * Requiere pesos no negativos.
 */
public class DeltaStepping {
    // Valor que representa una distancia infinita (vertice no alcanzado)
    public static final long INF = Long.MAX_VALUE / 4;
    // Valor de delta que representa "infinito": todas las aristas son ligeras
    public static final long DELTA_INFINITO = Long.MAX_VALUE;
    // Marca de "el vertice no esta en ninguna cubeta"
    private static final long SIN_CUBETA = -1;

    /*
     * Resultado: lo que devuelve el algoritmo (distancias y contadores).
     */
    public static class Resultado {
        // Atributos
        private long[] distancias;
        private long relajaciones;      // veces que se evalua d[u] + w(u, v) < d[v]
        private int cubetasProcesadas;  // cubetas no vacias procesadas (iteraciones)

        // Metodos
        public Resultado(long[] distancias, long relajaciones, int cubetasProcesadas) {
            this.distancias = distancias;
            this.relajaciones = relajaciones;
            this.cubetasProcesadas = cubetasProcesadas;
        }
        public long[] recuperarDistancias() {
            return distancias;
        }
        public long recuperarRelajaciones() {
            return relajaciones;
        }
        public int recuperarCubetasProcesadas() {
            return cubetasProcesadas;
        }
    }

    /*
     * Cubeta: lista de vertices guardada en un arreglo de enteros.
     * Para sacar un vertice se cambia por el ultimo, asi cuesta O(1).
     */
    private static class Cubeta {
        // Atributos
        private int[] vertices = new int[4];
        private int cantidad = 0;
    }

    // Atributos (datos de una ejecucion)
    private long delta;
    private long[] dist;
    private TreeMap<Long, Cubeta> cubetas;  // indice de cubeta -> cubeta no vacia
    private long[] cubetaDe;                 // cubeta en la que esta cada vertice
    private int[] posicion;                  // posicion del vertice dentro de su cubeta
    private long relajaciones;

    // Crear
    private DeltaStepping(int vertices, long delta) {
        this.delta = delta;
        this.dist = new long[vertices];
        this.cubetaDe = new long[vertices];
        this.posicion = new int[vertices];
        this.cubetas = new TreeMap<Long, Cubeta>();
        this.relajaciones = 0;
        for (int i = 0; i < vertices; i++) {
            dist[i] = INF;
            cubetaDe[i] = SIN_CUBETA;
        }
    }

    /*
     * ejecutar(vertices, aristas, origen, delta): aplica Delta-Stepping.
     * aristas: arreglo de aristas; cada una es {u, v, peso}, con peso >= 0
     * delta: ancho de las cubetas (> 0); DELTA_INFINITO para "infinito"
     */
    public static Resultado ejecutar(int vertices, int[][] aristas, int origen, long delta) {
        if (delta <= 0) {
            throw new RuntimeException("Error: delta debe ser positivo!!");
        }
        DeltaStepping ds = new DeltaStepping(vertices, delta);
        return ds.resolver(aristas, origen);
    }

    // Crea un arreglo de listas vacias (una por vertice)
    @SuppressWarnings("unchecked")
    private static ArrayList<int[]>[] nuevasListas(int vertices) {
        ArrayList<int[]>[] listas = new ArrayList[vertices];
        for (int i = 0; i < vertices; i++) {
            listas[i] = new ArrayList<int[]>();
        }
        return listas;
    }

    private Resultado resolver(int[][] aristas, int origen) {
        int vertices = dist.length;

        // 1. Listas de adyacencia, con aristas ligeras y pesadas separadas
        ArrayList<int[]>[] ligeras = nuevasListas(vertices);
        ArrayList<int[]>[] pesadas = nuevasListas(vertices);
        for (int[] a : aristas) {
            if (a[2] < 0) {
                throw new RuntimeException("Error: Delta-Stepping requiere pesos no negativos!!");
            }
            if (a[2] <= delta) {
                ligeras[a[0]].add(a);
            } else {
                pesadas[a[0]].add(a);
            }
        }

        // 2. El origen empieza en la cubeta 0
        dist[origen] = 0;
        moverACubeta(origen, 0);
        int cubetasProcesadas = 0;

        boolean[] yaProcesado = new boolean[vertices];
        int[] procesados = new int[vertices];

        // 3. Procesar siempre la primera cubeta no vacia
        while (!cubetas.isEmpty()) {
            long actual = cubetas.firstKey();
            int cantidadProcesados = 0;

            // 3a. Aristas ligeras: se repite mientras la cubeta vuelva a llenarse
            while (cubetas.containsKey(actual)) {
                Cubeta pendientes = cubetas.remove(actual);
                // Los vertices salen de la cubeta antes de relajar sus aristas
                for (int i = 0; i < pendientes.cantidad; i++) {
                    int u = pendientes.vertices[i];
                    cubetaDe[u] = SIN_CUBETA;
                    if (!yaProcesado[u]) {
                        yaProcesado[u] = true;
                        procesados[cantidadProcesados++] = u;
                    }
                }
                for (int i = 0; i < pendientes.cantidad; i++) {
                    int u = pendientes.vertices[i];
                    for (int[] a : ligeras[u]) {
                        relajar(a[1], dist[u] + a[2]);
                    }
                }
            }
            cubetasProcesadas++; // cada cubeta cuenta una sola vez

            // 3b. Aristas pesadas: una sola vez por vertice procesado
            for (int i = 0; i < cantidadProcesados; i++) {
                int u = procesados[i];
                yaProcesado[u] = false;
                for (int[] a : pesadas[u]) {
                    relajar(a[1], dist[u] + a[2]);
                }
            }
        }

        return new Resultado(dist, relajaciones, cubetasProcesadas);
    }

    // Relaja la arista que llega a v: si la nueva distancia es menor, mueve v de cubeta
    private void relajar(int v, long nuevaDistancia) {
        relajaciones++;
        if (nuevaDistancia < dist[v]) {
            dist[v] = nuevaDistancia;
            moverACubeta(v, indiceCubeta(nuevaDistancia));
        }
    }

    // Indice de la cubeta que corresponde a una distancia
    private long indiceCubeta(long distancia) {
        return distancia / delta; // con DELTA_INFINITO siempre da 0
    }

    // Saca a v de su cubeta anterior (si estaba en una) y lo pone en la cubeta indicada
    private void moverACubeta(int v, long nueva) {
        long anterior = cubetaDe[v];
        if (anterior == nueva) {
            return; // ya esta en esa cubeta
        }
        if (anterior != SIN_CUBETA) {
            Cubeta c = cubetas.get(anterior);
            int p = posicion[v];
            c.cantidad--;
            int ultimo = c.vertices[c.cantidad];   // el ultimo ocupa el lugar de v
            c.vertices[p] = ultimo;
            posicion[ultimo] = p;
            if (c.cantidad == 0) {
                cubetas.remove(anterior);
            }
        }
        Cubeta c = cubetas.get(nueva);
        if (c == null) {
            c = new Cubeta();
            cubetas.put(nueva, c);
        }
        if (c.cantidad == c.vertices.length) {         // arreglo lleno: duplicar tamano
            int[] mayor = new int[c.cantidad * 2];
            for (int i = 0; i < c.cantidad; i++) {
                mayor[i] = c.vertices[i];
            }
            c.vertices = mayor;
        }
        posicion[v] = c.cantidad;
        c.vertices[c.cantidad] = v;
        c.cantidad++;
        cubetaDe[v] = nueva;
    }
}
