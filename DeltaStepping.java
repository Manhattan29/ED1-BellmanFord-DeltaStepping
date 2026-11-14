import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * Algoritmo Delta-Stepping (version secuencial).
 *
 * Agrupa los vertices en cubetas de ancho delta segun su distancia tentativa.
 * Procesa siempre la primera cubeta no vacia: primero relaja las aristas
 * ligeras (peso <= delta) hasta que la cubeta quede vacia, y despues relaja
 * una vez las aristas pesadas (peso > delta) de los vertices procesados.
 * Requiere pesos no negativos.
 */
public class DeltaStepping {

    /** Valor que representa una distancia infinita (vertice no alcanzado). */
    public static final long INF = Long.MAX_VALUE / 4;

    /** Valor de delta que representa "infinito": todas las aristas son ligeras. */
    public static final long DELTA_INFINITO = Long.MAX_VALUE;

    /** Lo que devuelve el algoritmo: distancias y contadores. */
    public static class Resultado {
        public long[] distancias;
        public long relajaciones;      // veces que se evalua d[u] + w(u, v) < d[v]
        public int cubetasProcesadas;  // cubetas no vacias que se procesaron (iteraciones)
    }

    /**
     * Una cubeta: lista de vertices guardada en un arreglo de enteros.
     * Para sacar un vertice se cambia por el ultimo, asi cuesta O(1).
     */
    private static class Cubeta {
        int[] vertices = new int[4];
        int cantidad = 0;
    }

    private static final long SIN_CUBETA = -1;

    // Datos que se usan durante una ejecucion
    private final long delta;
    private final long[] dist;
    private final TreeMap<Long, Cubeta> cubetas = new TreeMap<>(); // indice -> cubeta no vacia
    private final long[] cubetaDe;   // en que cubeta esta cada vertice (SIN_CUBETA si en ninguna)
    private final int[] posicion;    // posicion del vertice dentro de su cubeta
    private long relajaciones = 0;

    private DeltaStepping(int vertices, long delta) {
        this.delta = delta;
        this.dist = new long[vertices];
        this.cubetaDe = new long[vertices];
        this.posicion = new int[vertices];
        Arrays.fill(dist, INF);
        Arrays.fill(cubetaDe, SIN_CUBETA);
    }

    /**
     * Ejecuta Delta-Stepping.
     *
     * @param vertices cantidad de vertices (numerados de 0 a vertices - 1)
     * @param aristas  arreglo de aristas; cada una es {u, v, peso}, con peso >= 0
     * @param origen   vertice de origen
     * @param delta    ancho de las cubetas (> 0); DELTA_INFINITO para "infinito"
     */
    public static Resultado ejecutar(int vertices, int[][] aristas, int origen, long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("Delta debe ser positivo.");
        }
        DeltaStepping ds = new DeltaStepping(vertices, delta);
        return ds.resolver(aristas, origen);
    }

    /** Separa las aristas de cada vertice en ligeras y pesadas. */
    @SuppressWarnings("unchecked")
    private static List<int[]>[] nuevasListas(int vertices) {
        List<int[]>[] listas = new List[vertices];
        for (int i = 0; i < vertices; i++) {
            listas[i] = new ArrayList<>();
        }
        return listas;
    }

    private Resultado resolver(int[][] aristas, int origen) {
        int vertices = dist.length;

        // 1. Listas de adyacencia, con aristas ligeras y pesadas separadas
        List<int[]>[] ligeras = nuevasListas(vertices);
        List<int[]>[] pesadas = nuevasListas(vertices);
        for (int[] a : aristas) {
            if (a[2] < 0) {
                throw new IllegalArgumentException("Delta-Stepping requiere pesos no negativos.");
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

        Resultado r = new Resultado();
        r.distancias = dist;
        r.relajaciones = relajaciones;
        r.cubetasProcesadas = cubetasProcesadas;
        return r;
    }

    /** Relaja la arista que llega a v: si la nueva distancia es menor, mueve v de cubeta. */
    private void relajar(int v, long nuevaDistancia) {
        relajaciones++;
        if (nuevaDistancia < dist[v]) {
            dist[v] = nuevaDistancia;
            moverACubeta(v, indiceCubeta(nuevaDistancia));
        }
    }

    /** Indice de la cubeta que corresponde a una distancia. */
    private long indiceCubeta(long distancia) {
        return distancia / delta; // con DELTA_INFINITO siempre da 0
    }

    /** Saca a v de su cubeta anterior (si estaba en una) y lo pone en la cubeta indicada. */
    private void moverACubeta(int v, long nueva) {
        long anterior = cubetaDe[v];
        if (anterior == nueva) {
            return; // ya esta en esa cubeta
        }
        if (anterior != SIN_CUBETA) {
            Cubeta c = cubetas.get(anterior);
            int p = posicion[v];
            int ultimo = c.vertices[--c.cantidad];   // el ultimo ocupa el lugar de v
            c.vertices[p] = ultimo;
            posicion[ultimo] = p;
            if (c.cantidad == 0) {
                cubetas.remove(anterior);
            }
        }
        Cubeta c = cubetas.computeIfAbsent(nueva, k -> new Cubeta());
        if (c.cantidad == c.vertices.length) {
            c.vertices = Arrays.copyOf(c.vertices, c.cantidad * 2);
        }
        posicion[v] = c.cantidad;
        c.vertices[c.cantidad++] = v;
        cubetaDe[v] = nueva;
    }

    /** Prueba rapida del algoritmo. */
    public static void main(String[] args) {
        int[][] aristas = {
            {0, 1, 4}, {0, 2, 2}, {2, 1, 1}, {1, 3, 5}, {2, 3, 8}
        };
        Resultado r = ejecutar(4, aristas, 0, 3);

        System.out.println("=== RESULTADOS DELTA-STEPPING (delta = 3) ===");
        for (int i = 0; i < r.distancias.length; i++) {
            System.out.println("Distancia al vertice " + i + ": " + r.distancias[i]);
        }
        System.out.println("Relajaciones: " + r.relajaciones);
        System.out.println("Cubetas procesadas: " + r.cubetasProcesadas);
    }
}
