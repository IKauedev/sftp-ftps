# Java FTPS/SFTP Client

Cliente Java para transferência segura de arquivos via FTPS (FTP sobre TLS/SSL) e SFTP (SSH File Transfer Protocol).

Oferece funcionalidades para:
* Conectar/desconectar a servidores FTPS e SFTP
* Upload e download recursivo de arquivos e diretórios
* Suporte a wildcards (`*`) em nomes de arquivos para múltiplos arquivos
* Criação automática de diretórios remotos e locais, se inexistentes
* Remoção dos arquivos remotos após download (para SFTP)

## Tecnologias

* Java 8+
* Biblioteca [Apache Commons Net](https://commons.apache.org/proper/commons-net/) para FTPS
* Biblioteca [JSch](http://www.jcraft.com/jsch/) para SFTP


## Como usar

### Instanciar o cliente FTPS

```java
FTPS ftps = new FTPS("host", 21, "username", "password");
```

### Conectar

```java
boolean connected = ftps.connect();
```

### Upload de arquivos/diretórios locais para servidor FTPS

```java
boolean sucesso = ftps.moveToRemote("C:/local/files/*.txt", "/remote/path/", true);
```

### Download de arquivos/diretórios do servidor FTPS para local

```java
boolean sucesso = ftps.moveToLocal("/remote/path/*.csv", "C:/local/downloads", true);
```

### Desconectar

```java
ftps.disconnect();
```


### Usar cliente SFTP

```java
SFTP sftp = new SFTP("host", 22, "username", "password");

sftp.connect();

sftp.moveToRemote("C:/local/files/", "/remote/path/", true);

sftp.moveToLocal("/remote/path/*.txt", "C:/local/downloads/", true);

sftp.disconnect();
```

## Observações

* Para o FTPS, o cliente cria diretórios remotos automaticamente antes do upload.
* Para o SFTP, o cliente cria diretórios locais e remotos conforme necessário.
* Wildcards (`*`) são suportados para múltiplos arquivos.
* Após download via SFTP, os arquivos remotos são apagados.

## Licença

Este projeto é open-source e pode ser usado e adaptado livremente.
