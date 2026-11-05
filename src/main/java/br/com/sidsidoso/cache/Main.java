package br.com.sidsidoso.cache;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Demonstracao do cache LRU: operacoes basicas e simulacoes de taxa de acerto. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        demoOperacoesBasicas();
        demoCacheDePaginas();
        demoVarreduraCiclica();
        demoConsultas();
    }

    private static void demoOperacoesBasicas() {
        titulo("1. Operacoes basicas (capacidade 3)");
        CacheLRU<String, Integer> cache = new CacheLRU<>(3,
                (k, v) -> System.out.printf("   ** despejo: %s=%d (menos recente)%n", k, v));
        System.out.println("ordem exibida: da mais recente para a menos recente");
        cache.colocar("a", 1);
        cache.colocar("b", 2);
        cache.colocar("c", 3);
        System.out.printf("colocar a, b, c   -> %s%n", cache);
        System.out.printf("obter(a)          -> %d, cache = %s%n", cache.obter("a"), cache);
        System.out.println("colocar(d, 4):");
        cache.colocar("d", 4);
        System.out.printf("                     cache = %s%n", cache);
        System.out.printf("obter(b)          -> %s (foi despejado)%n", cache.obter("b"));
        System.out.printf("colocar(c, 30)    -> anterior %d, cache = %s%n", cache.colocar("c", 30), cache);
        System.out.printf("contem(a)         -> %b (nao altera a ordem: %s)%n", cache.contem("a"), cache);
        System.out.printf("remover(d)        -> %d, cache = %s%n", cache.remover("d"), cache);
        System.out.printf("acertos=%d faltas=%d despejos=%d taxa=%s%n",
                cache.acertos(), cache.faltas(), cache.despejos(), pct(cache.taxaAcerto()));
    }

    /**
     * Simula um cache de paginas de disco: 100 mil acessos a 2 000 paginas com
     * distribuicao de Zipf (poucas paginas muito populares, cauda longa), que e
     * o padrao tipico de acesso a dados reais.
     */
    private static void demoCacheDePaginas() {
        titulo("2. Cache de paginas: 100000 acessos, 2000 paginas, distribuicao de Zipf (s=1)");
        int paginas = 2_000;
        int[] acessos = gerarZipf(new Random(42), paginas, 1.0, 100_000);
        System.out.printf("%10s  %8s  %8s  %8s%n", "capacidade", "acertos", "faltas", "taxa");
        for (int capacidade : new int[] {1, 10, 50, 100, 200, 500, 1_000, 2_000}) {
            CacheLRU<Integer, String> cache = new CacheLRU<>(capacidade);
            for (int pagina : acessos) {
                cache.obterOuCalcular(pagina, p -> "conteudo da pagina " + p);
            }
            System.out.printf("%10d  %8d  %8d  %8s  %s%n", capacidade, cache.acertos(), cache.faltas(),
                    pct(cache.taxaAcerto()), barra(cache.taxaAcerto()));
        }
        System.out.println("(com capacidade 2000 todas as paginas cabem; so restam as faltas compulsorias)");
    }

    /** Pior caso do LRU: varredura ciclica de um conjunto um pouco maior que o cache. */
    private static void demoVarreduraCiclica() {
        titulo("3. Pior caso do LRU: varredura ciclica de 100 paginas (0, 1, ..., 99, 0, 1, ...)");
        System.out.printf("%10s  %8s  %8s  %8s%n", "capacidade", "acertos", "faltas", "taxa");
        for (int capacidade : new int[] {50, 99, 100}) {
            CacheLRU<Integer, Integer> cache = new CacheLRU<>(capacidade);
            for (int volta = 0; volta < 100; volta++) {
                for (int pagina = 0; pagina < 100; pagina++) {
                    cache.obterOuCalcular(pagina, p -> p);
                }
            }
            System.out.printf("%10d  %8d  %8d  %8s%n", capacidade, cache.acertos(), cache.faltas(),
                    pct(cache.taxaAcerto()));
        }
        System.out.println("Faltando uma unica posicao, o LRU sempre despeja justamente a proxima pagina pedida.");
    }

    /** Cache na frente de uma "consulta lenta" (simulada), padrao cache-aside. */
    private static void demoConsultas() {
        titulo("4. Cache de consultas (cache-aside) com ouvinte de despejo");
        long[] consultasAoBanco = {0};
        long[] despejados = {0};
        CacheLRU<String, String> cache = new CacheLRU<>(64, (sql, resultado) -> despejados[0]++);
        String[] tabelas = {"clientes", "pedidos", "produtos", "estoque", "notas"};
        Random rnd = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            // 90% das consultas vem de 40 "telas" frequentes; 10% sao ad hoc
            int id = rnd.nextInt(10) < 9 ? rnd.nextInt(40) : 40 + rnd.nextInt(5_000);
            String sql = "SELECT * FROM " + tabelas[id % tabelas.length] + " WHERE id = " + id;
            cache.obterOuCalcular(sql, q -> {
                consultasAoBanco[0]++;
                return "resultado(" + q.hashCode() + ")";
            });
        }
        System.out.println("consultas feitas pela aplicacao : 20000");
        System.out.println("consultas que chegaram ao banco : " + consultasAoBanco[0]);
        System.out.println("taxa de acerto do cache         : " + pct(cache.taxaAcerto()));
        System.out.println("despejos avisados ao ouvinte    : " + despejados[0]);
        System.out.println("tamanho final / capacidade      : " + cache.tamanho() + " / " + cache.capacidade());
        StringBuilder recentes = new StringBuilder();
        int n = 0;
        for (var entrada : cache) {
            if (n++ == 3) {
                break;
            }
            recentes.append("\n   ").append(entrada.getKey());
        }
        System.out.println("3 entradas mais recentes        :" + recentes);
    }

    // ------------------------------------------------------------ auxiliares

    /** Gera {@code n} acessos em {@code [0, paginas)} seguindo uma lei de Zipf com expoente {@code s}. */
    static int[] gerarZipf(Random rnd, int paginas, double s, int n) {
        double[] acumulada = new double[paginas];
        double soma = 0;
        for (int i = 0; i < paginas; i++) {
            soma += 1.0 / Math.pow(i + 1, s);
            acumulada[i] = soma;
        }
        int[] acessos = new int[n];
        for (int i = 0; i < n; i++) {
            int pos = Arrays.binarySearch(acumulada, rnd.nextDouble() * soma);
            acessos[i] = pos >= 0 ? pos : -pos - 1;
        }
        return acessos;
    }

    private static String pct(double taxa) {
        return String.format(Locale.ROOT, "%.1f%%", taxa * 100);
    }

    private static String barra(double taxa) {
        return "#".repeat((int) Math.round(taxa * 40));
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println(texto);
        System.out.println("=".repeat(texto.length()));
    }
}
