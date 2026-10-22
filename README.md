# Sans Keyboard

Teclado Android com visual e funções inspirados no teclado Samsung (One UI), em que as teclas de destaque assumem a cor do app em que você está digitando.

Baseado no [HeliBoard](https://github.com/Helium314/HeliBoard) (GPL-3.0). O README original está em [README-HeliBoard.md](README-HeliBoard.md).

## O que muda em relação ao HeliBoard

- **Cor adaptativa por app**: a tecla Enter, a tecla pressionada, o Shift ativo e os destaques pegam a cor dominante do ícone do app aberto (verde no WhatsApp, laranja no Claude etc.). Pode ser desligada em *Configurações → Aparência*.
- **Teclas de vidro**: pretas em cima e transparentes embaixo, com contorno fino de vidro (como ícones glass). O fundo do teclado é o próprio app: *vidro fosco* (app desfocado atrás, padrão) ou *transparente*. Em *Aparência → Teclas de vidro*.
- **Vidro esfumaçado** (padrão): painel fosco com cantos arredondados, teclas cinza translúcidas que brilham na cor do app ao serem pressionadas.
- **12 sons de teclado físico**: Thocky (padrão), Creamy, Clacky, Cherry MX Blue/Brown/Red, Holy Panda, Alpaca, Topre, máquina de escrever, IBM de mola e Alps azul, em *Preferências → Som das teclas*.
- **Cor manual por app**: em *Aparência → Cor manual por app*, escolha um app e defina a cor. "Padrão" volta para a cor automática.
- **Padrões estilo Samsung**: fileira de números fixa, símbolos pequenos no canto das teclas (toque longo), teclas arredondadas com fundo, temas "Sans claro" e "Sans escuro", cursor ao deslizar no espaço.
- **Sugestão de colar**: o texto que você acabou de copiar aparece na barra de sugestões por alguns minutos; um toque cola.
- Digitação por deslize desativada por padrão.

## Como instalar

Cada envio para a branch `main` gera o APK automaticamente (GitHub Actions). Baixe o `SansKeyboard.apk` na página **Releases → latest**, instale e ative em *Configurações do Android → Teclado na tela*.

## Assinatura

O APK é assinado com a chave em `signing/sans.jks`, para que cada versão nova instale por cima da anterior. Se o repositório for público, troque essa chave por um segredo do GitHub antes de distribuir para outras pessoas.
