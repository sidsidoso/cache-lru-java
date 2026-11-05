package br.com.sidsidoso.cache;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

/**
 * Testes com um mini framework proprio (sem JUnit). Cada teste e um metodo
 * estatico registrado em {@link #main}; uma falha de assercao lanca
 * {@link AssertionError}. Sai com codigo 1 se algum teste falhar.
 */
public final class Testes {

    private static int passaram;
    private static final List<String> falhas = new ArrayList<>();

    private Testes() {
    }

    public static void main(String[] args) {
        teste("cache novo esta vazio", Testes::novoVazio);
        teste("capacidade invalida", Testes::capacidadeInvalida);
        teste("rejeita chave e valor null", Testes::rejeitaNull);
        teste("colocar e obter", Testes::colocarEObter);
        teste("despeja o menos recentemente usado", Testes::despejaMenosRecente);
        teste("obter renova a entrada", Testes::obterRenova);
        teste("colocar em chave existente atualiza e renova", Testes::atualizarRenova);
        teste("contem nao altera ordem nem estatisticas", Testes::contemNaoAltera);
        teste("capacidade 1", Testes::capacidadeUm);
        teste("ouvinte de despejo recebe chave e valor", Testes::ouvinteDespejo);
        teste("remover nao conta como despejo", Testes::remover);
        teste("limpar", Testes::limpar);
        teste("estatisticas: acertos, faltas e taxa", Testes::estatisticas);
        teste("obterOuCalcular calcula so na falta", Testes::obterOuCalcular);
        teste("mais e menos recente", Testes::maisEMenosRecente);
        teste("iteracao do mais para o menos recente", Testes::iteracao);
        teste("iterador em cache vazio", Testes::iteradorVazio);
        teste("iterador e fail-fast (inclusive com obter)", Testes::iteradorFailFast);
        teste("lista encadeada consistente nos dois sentidos", Testes::listaConsistente);
        teste("operacoes aleatorias vs LinkedHashMap (oraculo)", Testes::aleatorioContraLinkedHashMap);

        System.out.println();
        if (!falhas.isEmpty()) {
            System.out.println(falhas.size() + " teste(s) falharam:");
            falhas.forEach(f -> System.out.println("  - " + f));
            System.out.println(passaram + " testes passaram");
            System.exit(1);
        }
        System.out.println(passaram + " testes passaram");
    }

    // ------------------------------------------------------------ mini framework

    private interface CorpoTeste {
        void executar() throws Exception;
    }

    private static void teste(String nome, CorpoTeste corpo) {
        try {
            corpo.executar();
            passaram++;
            System.out.println("[ OK ] " + nome);
        } catch (Throwable t) {
            falhas.add(nome + ": " + t);
            System.out.println("[FALHOU] " + nome + " -> " + t);
        }
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    private static void igual(Object esperado, Object obtido) {
        if (!Objects.equals(esperado, obtido)) {
            throw new AssertionError("esperado <" + esperado + "> mas foi <" + obtido + ">");
        }
    }

    private static <E extends Throwable> E lanca(Class<E> tipo, CorpoTeste corpo) {
        try {
            corpo.executar();
        } catch (Throwable t) {
            if (tipo.isInstance(t)) {
                return tipo.cast(t);
            }
            throw new AssertionError("esperava " + tipo.getSimpleName() + " mas foi " + t, t);
        }
        throw new AssertionError("esperava " + tipo.getSimpleName() + " mas nada foi lancado");
    }

    private static <K, V> List<K> chaves(CacheLRU<K, V> cache) {
        List<K> l = new ArrayList<>();
        cache.forEach(e -> l.add(e.getKey()));
        return l;
    }

    /** Cache com algumas chaves inseridas em ordem; a ultima fica como a mais recente. */
    private static CacheLRU<String, Integer> cacheCom(int capacidade, String... chaves) {
        CacheLRU<String, Integer> c = new CacheLRU<>(capacidade);
        for (int i = 0; i < chaves.length; i++) {
            c.colocar(chaves[i], i + 1);
        }
        return c;
    }

    // ------------------------------------------------------------ casos

    private static void novoVazio() {
        CacheLRU<String, Integer> c = new CacheLRU<>(5);
        verificar(c.vazio(), "deveria estar vazio");
        igual(0, c.tamanho());
        igual(5, c.capacidade());
        igual(null, c.maisRecente());
        igual(null, c.menosRecente());
        igual("[]", c.toString());
        igual(0.0, c.taxaAcerto());
    }

    private static void capacidadeInvalida() {
        lanca(IllegalArgumentException.class, () -> new CacheLRU<String, String>(0));
        lanca(IllegalArgumentException.class, () -> new CacheLRU<String, String>(-3));
    }

    private static void rejeitaNull() {
        CacheLRU<String, Integer> c = new CacheLRU<>(2);
        lanca(NullPointerException.class, () -> c.colocar(null, 1));
        lanca(NullPointerException.class, () -> c.colocar("a", null));
        lanca(NullPointerException.class, () -> c.obter(null));
        lanca(NullPointerException.class, () -> c.remover(null));
        lanca(NullPointerException.class, () -> c.contem(null));
        verificar(c.vazio(), "nada deveria ter sido inserido");
    }

    private static void colocarEObter() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        igual(1, c.obter("a"));
        igual(2, c.obter("b"));
        igual(3, c.obter("c"));
        igual(null, c.obter("x"));
        igual(3, c.tamanho());
    }

    private static void despejaMenosRecente() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        c.colocar("d", 4);
        verificar(!c.contem("a"), "a deveria ter sido despejado");
        igual(List.of("d", "c", "b"), chaves(c));
        c.colocar("e", 5);
        igual(List.of("e", "d", "c"), chaves(c));
        igual(2L, c.despejos());
        igual(3, c.tamanho());
    }

    private static void obterRenova() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        c.obter("a");                       // a passa a ser o mais recente
        c.colocar("d", 4);                  // b e o menos recente agora
        igual(List.of("d", "a", "c"), chaves(c));
    }

    private static void atualizarRenova() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        igual(1, c.colocar("a", 10));
        igual(10, c.obter("a"));
        igual(3, c.tamanho());
        igual(0L, c.despejos());
        c.colocar("d", 4);
        igual(List.of("d", "a", "c"), chaves(c));
    }

    private static void contemNaoAltera() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        verificar(c.contem("a"), "deveria conter a");
        verificar(!c.contem("z"), "nao deveria conter z");
        igual(List.of("c", "b", "a"), chaves(c));
        igual(0L, c.acertos());
        igual(0L, c.faltas());
    }

    private static void capacidadeUm() {
        CacheLRU<Integer, Integer> c = new CacheLRU<>(1);
        for (int i = 0; i < 10; i++) {
            c.colocar(i, i * i);
            igual(1, c.tamanho());
            igual(i * i, c.obter(i));
            if (i > 0) {
                igual(null, c.obter(i - 1));
            }
        }
        igual(9L, c.despejos());
    }

    private static void ouvinteDespejo() {
        List<String> eventos = new ArrayList<>();
        CacheLRU<String, Integer> c = new CacheLRU<>(2, (k, v) -> eventos.add(k + "=" + v));
        c.colocar("a", 1);
        c.colocar("b", 2);
        c.obter("a");
        c.colocar("c", 3);   // despeja b
        c.colocar("a", 9);   // atualizacao: nao despeja
        c.colocar("d", 4);   // despeja c
        igual(List.of("b=2", "c=3"), eventos);
    }

    private static void remover() {
        List<String> eventos = new ArrayList<>();
        CacheLRU<String, Integer> c = new CacheLRU<>(3, (k, v) -> eventos.add(k));
        c.colocar("a", 1);
        c.colocar("b", 2);
        c.colocar("c", 3);
        igual(2, c.remover("b"));
        igual(null, c.remover("b"));
        igual(List.of("c", "a"), chaves(c));
        c.colocar("d", 4);   // ainda cabe: nada despejado
        igual(List.of(), eventos);
        igual(0L, c.despejos());
        igual(1, c.remover("a"));
        igual(3, c.remover("c"));
        igual(4, c.remover("d"));
        verificar(c.vazio(), "deveria ficar vazio");
        igual(null, c.menosRecente());
    }

    private static void limpar() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        c.obter("a");
        c.limpar();
        verificar(c.vazio(), "deveria estar vazio");
        igual("[]", c.toString());
        igual(1L, c.acertos()); // estatisticas preservadas
        c.colocar("x", 1);
        c.colocar("y", 2);
        c.colocar("z", 3);
        c.colocar("w", 4);
        igual(List.of("w", "z", "y"), chaves(c));
    }

    private static void estatisticas() {
        CacheLRU<Integer, Integer> c = new CacheLRU<>(2);
        c.obter(1);          // falta
        c.colocar(1, 1);
        c.obter(1);          // acerto
        c.obter(1);          // acerto
        c.obter(2);          // falta
        igual(2L, c.acertos());
        igual(2L, c.faltas());
        igual(0.5, c.taxaAcerto());
        c.zerarEstatisticas();
        igual(0L, c.acertos());
        igual(0L, c.faltas());
        igual(0.0, c.taxaAcerto());
    }

    private static void obterOuCalcular() {
        int[] chamadas = {0};
        CacheLRU<Integer, Integer> c = new CacheLRU<>(2);
        for (int i = 0; i < 5; i++) {
            igual(49, c.obterOuCalcular(7, k -> {
                chamadas[0]++;
                return k * k;
            }));
        }
        igual(1, chamadas[0]);
        igual(4L, c.acertos());
        igual(1L, c.faltas());
    }

    private static void maisEMenosRecente() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        igual("c", c.maisRecente());
        igual("a", c.menosRecente());
        c.obter("a");
        igual("a", c.maisRecente());
        igual("b", c.menosRecente());
    }

    private static void iteracao() {
        CacheLRU<String, Integer> c = cacheCom(4, "a", "b", "c", "d");
        c.obter("b");
        List<String> visto = new ArrayList<>();
        for (Map.Entry<String, Integer> e : c) {
            visto.add(e.getKey() + "=" + e.getValue());
        }
        igual(List.of("b=2", "d=4", "c=3", "a=1"), visto);
        igual("[b=2, d=4, c=3, a=1]", c.toString());
        igual(List.of("b", "d", "c", "a"), chaves(c)); // iterar nao altera a ordem
    }

    private static void iteradorVazio() {
        Iterator<Map.Entry<String, Integer>> it = new CacheLRU<String, Integer>(1).iterator();
        verificar(!it.hasNext(), "iterador de cache vazio nao tem proximo");
        lanca(NoSuchElementException.class, it::next);
    }

    private static void iteradorFailFast() {
        CacheLRU<String, Integer> c = cacheCom(3, "a", "b", "c");
        Iterator<Map.Entry<String, Integer>> it = c.iterator();
        it.next();
        c.obter("a");        // muda a ordem
        lanca(ConcurrentModificationException.class, it::next);

        Iterator<Map.Entry<String, Integer>> it2 = c.iterator();
        it2.next();
        c.remover("b");
        lanca(ConcurrentModificationException.class, it2::next);

        Iterator<Map.Entry<String, Integer>> it3 = c.iterator();
        c.obter("a");        // ja e o mais recente: ordem nao muda, iterador segue valido
        c.contem("c");
        igual("a", it3.next().getKey());
    }

    /** Percorre a lista interna pelas sentinelas nos dois sentidos via reflexao. */
    private static void listaConsistente() throws Exception {
        CacheLRU<Integer, Integer> c = new CacheLRU<>(50);
        Random rnd = new Random(3);
        for (int i = 0; i < 5_000; i++) {
            int k = rnd.nextInt(80);
            switch (rnd.nextInt(3)) {
                case 0 -> c.colocar(k, k);
                case 1 -> c.obter(k);
                default -> c.remover(k);
            }
        }
        var campoCabeca = CacheLRU.class.getDeclaredField("cabeca");
        var campoCauda = CacheLRU.class.getDeclaredField("cauda");
        campoCabeca.setAccessible(true);
        campoCauda.setAccessible(true);
        Object cabeca = campoCabeca.get(c);
        Object cauda = campoCauda.get(c);
        Class<?> classeNo = cabeca.getClass();
        var proximo = classeNo.getDeclaredField("proximo");
        var anterior = classeNo.getDeclaredField("anterior");
        var chave = classeNo.getDeclaredField("chave");
        proximo.setAccessible(true);
        anterior.setAccessible(true);
        chave.setAccessible(true);

        List<Object> ida = new ArrayList<>();
        for (Object no = proximo.get(cabeca); no != cauda; no = proximo.get(no)) {
            igual(no, proximo.get(anterior.get(no)));   // no.anterior.proximo == no
            ida.add(chave.get(no));
        }
        List<Object> volta = new ArrayList<>();
        for (Object no = anterior.get(cauda); no != cabeca; no = anterior.get(no)) {
            volta.add(0, chave.get(no));
        }
        igual(ida, volta);
        igual(c.tamanho(), ida.size());
    }

    /**
     * Oraculo: {@link LinkedHashMap} com {@code accessOrder = true} (get e put
     * movem a entrada para o fim) e {@code removeEldestEntry} limitando o
     * tamanho. Sua ordem de iteracao vai do menos para o mais recente, o
     * inverso do cache.
     */
    private static final class OraculoLRU<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int capacidade;
        final transient List<K> despejadas = new ArrayList<>();

        OraculoLRU(int capacidade) {
            super(16, 0.75f, true);
            this.capacidade = capacidade;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> maisAntiga) {
            if (size() > capacidade) {
                despejadas.add(maisAntiga.getKey());
                return true;
            }
            return false;
        }
    }

    private static void aleatorioContraLinkedHashMap() {
        Random rnd = new Random(42);
        for (int capacidade : new int[] {1, 2, 7, 64, 500}) {
            OraculoLRU<Integer, Integer> oraculo = new OraculoLRU<>(capacidade);
            List<Integer> despejadas = new ArrayList<>();
            CacheLRU<Integer, Integer> cache = new CacheLRU<>(capacidade, (k, v) -> despejadas.add(k));
            int universo = capacidade * 3 + 5;
            long acertosEsperados = 0;
            long faltasEsperadas = 0;
            for (int passo = 0; passo < 60_000; passo++) {
                int k = rnd.nextInt(universo);
                int r = rnd.nextInt(100);
                if (r < 45) {
                    int v = rnd.nextInt(1_000);
                    igual(oraculo.put(k, v), cache.colocar(k, v));
                } else if (r < 90) {
                    Integer esperado = oraculo.get(k);
                    if (esperado == null) {
                        faltasEsperadas++;
                    } else {
                        acertosEsperados++;
                    }
                    igual(esperado, cache.obter(k));
                } else if (r < 98) {
                    igual(oraculo.remove(k), cache.remover(k));
                } else {
                    igual(oraculo.containsKey(k), cache.contem(k));
                }
                igual(oraculo.size(), cache.tamanho());
                if (passo % 1_000 == 0) {
                    compararOrdem(oraculo, cache);
                }
            }
            compararOrdem(oraculo, cache);
            igual(oraculo.despejadas, despejadas);
            igual((long) despejadas.size(), cache.despejos());
            igual(acertosEsperados, cache.acertos());
            igual(faltasEsperadas, cache.faltas());
        }
    }

    private static void compararOrdem(OraculoLRU<Integer, Integer> oraculo, CacheLRU<Integer, Integer> cache) {
        List<Map.Entry<Integer, Integer>> esperado = new ArrayList<>();
        oraculo.entrySet().forEach(e -> esperado.add(0, Map.entry(e.getKey(), e.getValue())));
        List<Map.Entry<Integer, Integer>> obtido = new ArrayList<>();
        cache.forEach(obtido::add);
        igual(esperado, obtido);
        if (!esperado.isEmpty()) {
            igual(esperado.get(0).getKey(), cache.maisRecente());
            igual(esperado.get(esperado.size() - 1).getKey(), cache.menosRecente());
        }
    }
}
