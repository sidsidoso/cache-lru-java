# Cache LRU em Java

[![CI](https://github.com/sidsidoso/cache-lru-java/actions/workflows/ci.yml/badge.svg)](https://github.com/sidsidoso/cache-lru-java/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)

Implementação de um **cache LRU** (*Least Recently Used*) genérico de
capacidade fixa, feito do zero em Java 21 para a disciplina de Estrutura de
Dados. A lista duplamente encadeada é própria (com sentinelas) e o índice é um
`HashMap<K, No>`; juntos, eles garantem **`obter` e `colocar` em O(1)**.

Inclui:

- despejo automático da entrada **menos recentemente usada** quando o cache
  enche, com **callback de despejo**;
- **estatísticas** de acertos, faltas, despejos e taxa de acerto;
- `remover`, `limpar`, `contem` (sem mexer na ordem) e `obterOuCalcular`
  (padrão *cache-aside*);
- iteração da entrada **mais recente para a menos recente**, com iterador
  fail-fast;
- uma demonstração que simula um **cache de páginas** (acessos com
  distribuição de Zipf) e um **cache de consultas**, medindo a taxa de acerto
  para várias capacidades.

Sem dependências externas: compila com `javac` puro e os testes usam um mini
framework próprio.

## Conceito

Um cache guarda um subconjunto pequeno de dados caros de obter (páginas de
disco, respostas de banco, resultados de cálculo). Quando não há mais espaço,
alguém precisa sair: a política **LRU** despeja quem foi usado há mais tempo,
apostando que o passado recente prevê o futuro próximo (*localidade temporal*).

Para que tudo seja O(1), cada entrada vive em **duas estruturas ao mesmo
tempo**:

```
 HashMap<K, No>            lista duplamente encadeada (da mais para a menos recente)

 ┌───┬───┐
 │ b │ ●─┼───────────────────────────────────────────┐
 │ a │ ●─┼───────────────────────────────┐           │
 │ c │ ●─┼───────────────────┐           │           │
 └───┴───┘                   ▼           ▼           ▼
            ┌────────┐   ┌───────┐   ┌───────┐   ┌───────┐   ┌───────┐
            │ cabeça │◄─►│ c = 3 │◄─►│ a = 1 │◄─►│ b = 2 │◄─►│ cauda │
            └────────┘   └───────┘   └───────┘   └───────┘   └───────┘
             sentinela   mais recente            menos recente
                                                 (próxima vítima)
```

- **`obter(k)`**: o mapa entrega o nó; ele é desligado de onde está e
  religado logo após a cabeça: poucas atribuições de ponteiro, sem laços.
- **`colocar(k, v)`** com chave nova e cache cheio: o nó antes da cauda é
  desligado e removido do mapa (despejo), e o novo nó entra após a cabeça.

Exemplo com capacidade 3 (ordem: mais recente → menos recente):

```
 colocar a, b, c   [c, b, a]
 obter(a)          [a, c, b]      a renovado
 colocar(d)        [d, a, c]      b despejado (era o menos recente)
 obter(b)          falta          b já saiu
```

## Operações e complexidade

| Operação                      | Descrição                                               | Tempo           |
|-------------------------------|---------------------------------------------------------|-----------------|
| `obter(k)`                    | devolve o valor e renova a entrada; conta acerto/falta  | O(1) esperado   |
| `colocar(k, v)`               | insere/atualiza e renova; despeja o LRU se cheio        | O(1) esperado   |
| `obterOuCalcular(k, f)`       | `obter` e, na falta, calcula com `f` e guarda           | O(1) + custo de `f` |
| `remover(k)`                  | remove explicitamente (não conta como despejo)          | O(1) esperado   |
| `contem(k)`                   | consulta sem alterar ordem nem estatísticas             | O(1) esperado   |
| `maisRecente()` / `menosRecente()` | chaves das pontas da lista                         | O(1)            |
| `limpar()`                    | esvazia (estatísticas preservadas)                      | O(capacidade)   |
| `acertos()`, `faltas()`, `despejos()`, `taxaAcerto()` | estatísticas                    | O(1)            |
| `iterator()`                  | percorre do mais para o menos recente                   | O(n) no total   |

"Esperado" vem do `HashMap`: O(1) em média com uma boa função de hash.
Memória: O(capacidade), um nó (chave, valor, dois ponteiros) mais uma entrada
de mapa por elemento.

## Decisões de implementação e otimizações

- **Sentinelas de cabeça e cauda**: a lista nunca fica "vazia" de verdade,
  então inserir após a cabeça e desligar um nó não têm casos especiais para
  primeiro/último elemento nem testes de `null`.
- **O nó guarda a chave**: ao despejar o último nó da lista, é preciso
  removê-lo também do mapa; sem a chave no nó isso exigiria uma busca O(n).
- **`HashMap.newHashMap(capacidade)`** (Java 19+): o mapa já nasce com espaço
  para `capacidade` entradas sem ultrapassar o fator de carga, então nunca
  há *rehash* durante o uso.
- **Despejo antes da inserção**: quando a chave é nova e o cache está cheio, o
  LRU sai primeiro; assim o mapa nunca passa de `capacidade` elementos.
  Atualizar uma chave existente nunca despeja nada.
- **Callback de despejo** (`OuvinteDespejo`): chamado só para despejos por
  falta de espaço, depois que a entrada já saiu, permitindo *write-back*,
  liberação de recursos ou métricas. `remover` e `limpar` são explícitos e não
  disparam o callback.
- **`contem` não "toca" a entrada**: consultar presença não deve alterar a
  política de despejo nem distorcer a taxa de acerto.
- **Iterador fail-fast sensível à ordem**: num cache LRU até um `obter` é uma
  modificação estrutural (move um nó). O contador de modificações cobre isso,
  mas `obter` da entrada que já é a mais recente não conta, porque nada muda.
- **Sem `null`**: chaves e valores nulos são rejeitados, de modo que `null`
  em `obter` significa inequivocamente "não está no cache".
- **Por que não `LinkedHashMap`?** Ele já faz isso (com `accessOrder = true` e
  `removeEldestEntry`) e é justamente o **oráculo** dos testes. O objetivo
  aqui é mostrar a estrutura por dentro.

## Estrutura de pastas

```
cache-lru-java/
├── .github/workflows/ci.yml        # CI: testes + demo no GitHub Actions
├── src/
│   ├── main/java/br/com/sidsidoso/cache/
│   │   ├── CacheLRU.java           # HashMap + lista duplamente encadeada
│   │   ├── OuvinteDespejo.java     # callback de despejo
│   │   └── Main.java               # demonstração e simulações
│   └── test/java/br/com/sidsidoso/cache/
│       └── Testes.java             # mini framework de testes + casos
├── build.sh                        # build para Linux/macOS
├── build.ps1                       # build para Windows
├── LICENSE
└── README.md
```

## Como compilar, executar e testar

Pré-requisito: **JDK 21** ou superior (`javac` e `java` no `PATH`, ou
`JAVA_HOME` definido para o `build.ps1`).

### Com os scripts

```bash
# Linux / macOS
./build.sh          # só compila para out/
./build.sh run      # compila e executa a demonstração
./build.sh test     # compila e roda os testes
```

```powershell
# Windows (PowerShell)
.\build.ps1
.\build.ps1 run
.\build.ps1 test
```

Os scripts compilam com `-Xlint:all -Werror`, então qualquer aviso do
compilador quebra o build.

### Manualmente

```bash
javac --release 21 -Xlint:all -d out $(find src -name "*.java")
java -cp out br.com.sidsidoso.cache.Main      # demonstração
java -cp out br.com.sidsidoso.cache.Testes    # testes
```

O programa de testes imprime cada caso, termina com `N testes passaram` e sai
com código 1 se algum falhar. Entre os casos estão bordas (capacidade 1,
atualização sem despejo, `remover` e `limpar`, `null`), o iterador fail-fast,
a consistência dos ponteiros da lista nos dois sentidos (via reflexão) e
**300 mil operações aleatórias** (60 mil para cada capacidade 1, 2, 7, 64 e 500)
comparadas com um oráculo `LinkedHashMap(accessOrder = true)` com
`removeEldestEntry`: valores devolvidos, ordem completa de uso, sequência de
chaves despejadas, acertos e faltas.

## Exemplo de saída

Saída real de `./build.sh run`:

```
1. Operacoes basicas (capacidade 3)
===================================
ordem exibida: da mais recente para a menos recente
colocar a, b, c   -> [c=3, b=2, a=1]
obter(a)          -> 1, cache = [a=1, c=3, b=2]
colocar(d, 4):
   ** despejo: b=2 (menos recente)
                     cache = [d=4, a=1, c=3]
obter(b)          -> null (foi despejado)
colocar(c, 30)    -> anterior 3, cache = [c=30, d=4, a=1]
contem(a)         -> true (nao altera a ordem: [c=30, d=4, a=1])
remover(d)        -> 4, cache = [c=30, a=1]
acertos=1 faltas=1 despejos=1 taxa=50.0%

2. Cache de paginas: 100000 acessos, 2000 paginas, distribuicao de Zipf (s=1)
=============================================================================
capacidade   acertos    faltas      taxa
         1      2458     97542      2.5%  #
        10     18002     81998     18.0%  #######
        50     40398     59602     40.4%  ################
       100     50660     49340     50.7%  ####################
       200     61119     38881     61.1%  ########################
       500     75868     24132     75.9%  ##############################
      1000     87508     12492     87.5%  ###################################
      2000     98000      2000     98.0%  #######################################
(com capacidade 2000 todas as paginas cabem; so restam as faltas compulsorias)

3. Pior caso do LRU: varredura ciclica de 100 paginas (0, 1, ..., 99, 0, 1, ...)
================================================================================
capacidade   acertos    faltas      taxa
        50         0     10000      0.0%
        99         0     10000      0.0%
       100      9900       100     99.0%
Faltando uma unica posicao, o LRU sempre despeja justamente a proxima pagina pedida.

4. Cache de consultas (cache-aside) com ouvinte de despejo
==========================================================
consultas feitas pela aplicacao : 20000
consultas que chegaram ao banco : 2091
taxa de acerto do cache         : 89.5%
despejos avisados ao ouvinte    : 2027
tamanho final / capacidade      : 64 / 64
3 entradas mais recentes        :
   SELECT * FROM clientes WHERE id = 0
   SELECT * FROM notas WHERE id = 34
   SELECT * FROM clientes WHERE id = 20
```

Leitura dos resultados:

- **Zipf**: poucas páginas concentram a maior parte dos acessos, então um
  cache com 5% das páginas (100) já acerta metade das vezes, e com 25% (500)
  acerta três em cada quatro. Com todas as páginas cabendo, só sobram as
  2000 faltas compulsórias (primeiro acesso de cada página).
- **Varredura cíclica**: é o pior caso clássico do LRU. Se o ciclo é maior que
  o cache, mesmo que por uma única posição, toda página é despejada pouco
  antes de ser pedida de novo e a taxa de acerto é zero. Políticas como
  LRU-K, 2Q, ARC ou CLOCK-Pro foram criadas em parte para resistir a isso.
- **Consultas**: com 90% das consultas vindo de 40 "telas" frequentes, um
  cache de 64 entradas evita cerca de 9 em cada 10 idas ao banco.

## Referências

- CORMEN, T. H. et al. *Algoritmos: teoria e prática*. 3. ed. Elsevier, 2012.
  Capítulo 10 (listas duplamente encadeadas com sentinela) e Capítulo 11
  (tabelas de dispersão).
- TANENBAUM, A. S.; BOS, H. *Sistemas operacionais modernos*. 4. ed. Pearson,
  2016. Seção 3.4 (algoritmos de substituição de páginas, incluindo LRU).
- MEGIDDO, N.; MODHA, D. S. *ARC: A Self-Tuning, Low Overhead Replacement
  Cache*. USENIX FAST, 2003.
- [LRU Cache — LeetCode 146](https://leetcode.com/problems/lru-cache/)
- [Documentação de `java.util.LinkedHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html)
