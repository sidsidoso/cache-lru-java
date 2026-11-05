package br.com.sidsidoso.cache;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Cache LRU (<i>Least Recently Used</i>) de capacidade fixa.
 *
 * <p>Combina duas estruturas:
 * <ul>
 *   <li>um {@link HashMap} de chave para no, que localiza qualquer entrada em
 *       O(1) esperado;</li>
 *   <li>uma lista duplamente encadeada propria, com sentinelas de cabeca e
 *       cauda, que mantem as entradas da mais recente (logo apos a cabeca) para
 *       a menos recente (logo antes da cauda). Mover um no para a frente ou
 *       remover o ultimo custa O(1).</li>
 * </ul>
 * Quando o cache esta cheio e uma chave nova e inserida, a entrada menos
 * recentemente usada e despejada e o {@link OuvinteDespejo} (se houver) e
 * avisado.
 *
 * <p>Nao aceita chaves nem valores {@code null}. Nao e thread-safe.
 *
 * @param <K> tipo das chaves
 * @param <V> tipo dos valores
 */
public final class CacheLRU<K, V> implements Iterable<Map.Entry<K, V>> {

    /** No da lista duplamente encadeada. As sentinelas tem chave e valor nulos. */
    private static final class No<K, V> {
        final K chave;
        V valor;
        No<K, V> anterior;
        No<K, V> proximo;

        No(K chave, V valor) {
            this.chave = chave;
            this.valor = valor;
        }
    }

    private final int capacidade;
    private final HashMap<K, No<K, V>> mapa;
    /** Sentinela: {@code cabeca.proximo} e a entrada mais recente. */
    private final No<K, V> cabeca = new No<>(null, null);
    /** Sentinela: {@code cauda.anterior} e a entrada menos recente. */
    private final No<K, V> cauda = new No<>(null, null);
    private final OuvinteDespejo<K, V> ouvinte;

    private long acertos;
    private long faltas;
    private long despejos;
    /** Contador de modificacoes (inclusive mudancas de ordem), para o iterador fail-fast. */
    private int modificacoes;

    /**
     * Cria um cache vazio sem ouvinte de despejo.
     *
     * @param capacidade numero maximo de entradas (positivo)
     */
    public CacheLRU(int capacidade) {
        this(capacidade, null);
    }

    /**
     * Cria um cache vazio.
     *
     * @param capacidade numero maximo de entradas (positivo)
     * @param ouvinte chamado a cada despejo por falta de espaco; pode ser {@code null}
     * @throws IllegalArgumentException se a capacidade nao for positiva
     */
    public CacheLRU(int capacidade, OuvinteDespejo<K, V> ouvinte) {
        if (capacidade <= 0) {
            throw new IllegalArgumentException("capacidade deve ser positiva: " + capacidade);
        }
        this.capacidade = capacidade;
        this.ouvinte = ouvinte;
        // dimensiona o HashMap para nunca precisar de rehash (fator de carga 0.75)
        this.mapa = HashMap.newHashMap(capacidade);
        cabeca.proximo = cauda;
        cauda.anterior = cabeca;
    }

    // ------------------------------------------------------------ operacoes principais

    /**
     * Busca o valor associado a chave. Em caso de acerto, a entrada passa a ser
     * a mais recente. Conta um acerto ou uma falta nas estatisticas.
     *
     * @param chave chave procurada (nao nula)
     * @return valor associado, ou {@code null} se a chave nao estiver no cache
     */
    public V obter(K chave) {
        exigirNaoNulo(chave, "chave nula");
        No<K, V> no = mapa.get(chave);
        if (no == null) {
            faltas++;
            return null;
        }
        acertos++;
        moverParaFrente(no);
        return no.valor;
    }

    /**
     * Insere ou atualiza uma entrada, que passa a ser a mais recente. Se a
     * chave for nova e o cache estiver cheio, despeja a menos recente.
     *
     * @param chave chave (nao nula)
     * @param valor valor (nao nulo)
     * @return valor anterior da chave, ou {@code null} se ela nao existia
     */
    public V colocar(K chave, V valor) {
        exigirNaoNulo(chave, "chave nula");
        exigirNaoNulo(valor, "valor nulo");
        No<K, V> no = mapa.get(chave);
        if (no != null) {
            V anterior = no.valor;
            no.valor = valor;
            moverParaFrente(no);
            return anterior;
        }
        if (mapa.size() == capacidade) {
            despejarMenosRecente();
        }
        no = new No<>(chave, valor);
        mapa.put(chave, no);
        inserirAposCabeca(no);
        modificacoes++;
        return null;
    }

    /**
     * Devolve o valor em cache ou, numa falta, calcula-o com a funcao
     * fornecida, guarda e devolve. Padrao <i>cache-aside</i>.
     *
     * @param chave chave (nao nula)
     * @param calcular funcao (lenta) que produz o valor; nao pode devolver {@code null}
     * @return valor em cache ou recem-calculado
     */
    public V obterOuCalcular(K chave, Function<? super K, ? extends V> calcular) {
        V valor = obter(chave);
        if (valor == null) {
            valor = calcular.apply(chave);
            colocar(chave, valor);
        }
        return valor;
    }

    /**
     * Remove explicitamente uma entrada (nao conta como despejo e nao avisa o
     * ouvinte).
     *
     * @param chave chave (nao nula)
     * @return valor removido, ou {@code null} se a chave nao existia
     */
    public V remover(K chave) {
        exigirNaoNulo(chave, "chave nula");
        No<K, V> no = mapa.remove(chave);
        if (no == null) {
            return null;
        }
        desligar(no);
        modificacoes++;
        return no.valor;
    }

    /** Remove todas as entradas. As estatisticas sao preservadas. */
    public void limpar() {
        mapa.clear();
        cabeca.proximo = cauda;
        cauda.anterior = cabeca;
        modificacoes++;
    }

    /**
     * Indica se a chave esta no cache <b>sem</b> alterar a ordem de uso nem as
     * estatisticas.
     *
     * @param chave chave (nao nula)
     * @return {@code true} se presente
     */
    public boolean contem(K chave) {
        exigirNaoNulo(chave, "chave nula");
        return mapa.containsKey(chave);
    }

    /** @return chave mais recentemente usada, ou {@code null} se vazio */
    public K maisRecente() {
        return cabeca.proximo == cauda ? null : cabeca.proximo.chave;
    }

    /** @return chave menos recentemente usada (proxima a ser despejada), ou {@code null} se vazio */
    public K menosRecente() {
        return cauda.anterior == cabeca ? null : cauda.anterior.chave;
    }

    // ------------------------------------------------------------ tamanho e estatisticas

    /** @return quantidade de entradas */
    public int tamanho() {
        return mapa.size();
    }

    /** @return {@code true} se nao houver entradas */
    public boolean vazio() {
        return mapa.isEmpty();
    }

    /** @return capacidade maxima */
    public int capacidade() {
        return capacidade;
    }

    /** @return numero de chamadas a {@link #obter} que encontraram a chave */
    public long acertos() {
        return acertos;
    }

    /** @return numero de chamadas a {@link #obter} que nao encontraram a chave */
    public long faltas() {
        return faltas;
    }

    /** @return numero de entradas despejadas por falta de espaco */
    public long despejos() {
        return despejos;
    }

    /** @return acertos / (acertos + faltas), ou 0 se nao houve consultas */
    public double taxaAcerto() {
        long total = acertos + faltas;
        return total == 0 ? 0.0 : (double) acertos / total;
    }

    /** Zera acertos, faltas e despejos. */
    public void zerarEstatisticas() {
        acertos = 0;
        faltas = 0;
        despejos = 0;
    }

    // ------------------------------------------------------------ iteracao

    /**
     * Itera da entrada mais recente para a menos recente, sem alterar a ordem.
     * O iterador e fail-fast: qualquer insercao, remocao ou acesso que mude a
     * ordem durante a iteracao causa {@link ConcurrentModificationException}.
     */
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new Iterator<>() {
            private No<K, V> atual = cabeca.proximo;
            private final int modificacoesEsperadas = modificacoes;

            @Override
            public boolean hasNext() {
                return atual != cauda;
            }

            @Override
            public Map.Entry<K, V> next() {
                if (modificacoes != modificacoesEsperadas) {
                    throw new ConcurrentModificationException();
                }
                if (atual == cauda) {
                    throw new NoSuchElementException();
                }
                Map.Entry<K, V> entrada = Map.entry(atual.chave, atual.valor);
                atual = atual.proximo;
                return entrada;
            }
        };
    }

    /** @return entradas da mais para a menos recente, ex.: {@code [c=3, a=1, b=2]} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (No<K, V> no = cabeca.proximo; no != cauda; no = no.proximo) {
            if (no != cabeca.proximo) {
                sb.append(", ");
            }
            sb.append(no.chave).append('=').append(no.valor);
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------ lista encadeada

    private void moverParaFrente(No<K, V> no) {
        if (cabeca.proximo != no) {
            desligar(no);
            inserirAposCabeca(no);
            modificacoes++;
        }
    }

    private void despejarMenosRecente() {
        No<K, V> vitima = cauda.anterior;
        desligar(vitima);
        mapa.remove(vitima.chave);
        despejos++;
        modificacoes++;
        if (ouvinte != null) {
            ouvinte.despejado(vitima.chave, vitima.valor);
        }
    }

    /** Liga o no logo apos a sentinela da cabeca. Gracas as sentinelas, nao ha casos especiais. */
    private void inserirAposCabeca(No<K, V> no) {
        no.anterior = cabeca;
        no.proximo = cabeca.proximo;
        cabeca.proximo.anterior = no;
        cabeca.proximo = no;
    }

    private void desligar(No<K, V> no) {
        no.anterior.proximo = no.proximo;
        no.proximo.anterior = no.anterior;
        no.anterior = null;
        no.proximo = null;
    }

    private static void exigirNaoNulo(Object o, String descricao) {
        if (o == null) {
            throw new NullPointerException("o cache nao aceita " + descricao);
        }
    }
}
