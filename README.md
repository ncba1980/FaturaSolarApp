# FaturaSolarApp
App para fazer os cálculos de consumo, geração e estoque de energia solar. Como também exibir os valores a serem pagos.

# ☀️ App de Auditoria de Energia Solar Fotovoltaica (Grupo B/ANEEL)

![Android](https://shields.io)
![Kotlin](https://shields.io)
![License](https://shields.io)

Este é um aplicativo móvel nativo **offline-first** desenvolvido para solucionar a falta de transparência no faturamento de créditos de energia solar distribuída para consumidores do Grupo B, em total conformidade com a Resolução Normativa nº 1.000 da ANEEL e a Lei nº 14.300.

O projeto nasceu de uma necessidade real de mercado: traduzir a complexidade das faturas de energia em uma interface simples, permitindo ao usuário auditar, gerenciar e prever seus gastos e acúmulo de créditos de forma totalmente privada e segura.

## 🚀 Funcionalidades Principais (Visão de Produto)

*   **Motor de Cálculo de Alta Precisão:** Implementado com aritmética decimal exata para auditar faturas centavo por centavo, evitando erros de ponto flutuante.
*   **Gestão de Histórico Local:** Persistência de dados local (SQLite/Room) para armazenar e acompanhar o histórico de consumo, geração e reserva acumulada ao longo do ano.
*   **Geração de Relatórios em PDF:** Transformação automática dos dados mensais em relatórios gerenciais estruturados.
*   **Compartilhamento Sem Fricção:** Integração nativa com o Android para envio dos relatórios instantaneamente via WhatsApp, E-mail, Direct ou Bluetooth.
*   **Impressão Direta:** Botão dedicado e integrado ao sistema operacional para imprimir o relatório em PDF direto do celular.
*   **Foco em Privacidade:** Funcionamento 100% offline. O app limpa dados sensíveis (Nome, CPF, Endereço), garantindo a segurança da informação.

## 🛠️ Tecnologias Utilizadas

*   **Frontend/Mobile:** Kotlin Nativo & Android SDK (API 24+)
*   **Lógica e Prototipagem:** Python (Engenharia reversa das regras de negócio)
*   **Banco de Dados:** Persistência local estruturada
*   **UX/UI:** Interfaces responsivas para celulares e tablets validadas por inspeção heurística

---
*Este projeto foi desenvolvido como parte de um estudo de caso e Engenharia de Software aplicada ao setor elétrico brasileiro.*

