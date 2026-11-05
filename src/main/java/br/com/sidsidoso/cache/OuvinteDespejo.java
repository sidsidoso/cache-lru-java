package br.com.sidsidoso.cache;

/**
 * Callback avisado quando o {@link CacheLRU} despeja uma entrada por falta de
 * espaco. Util para gravar de volta (<i>write-back</i>) um valor modificado,
 * liberar recursos ou registrar metricas.
 *
 * @param <K> tipo das chaves
 * @param <V> tipo dos valores
 */
@FunctionalInterface
public interface OuvinteDespejo<K, V> {

    /**
     * Chamado depois que a entrada ja saiu do cache.
     *
     * @param chave chave despejada
     * @param valor valor que estava associado a chave
     */
    void despejado(K chave, V valor);
}
