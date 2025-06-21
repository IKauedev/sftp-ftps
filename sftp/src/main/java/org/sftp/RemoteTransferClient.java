package org.sftp;

public interface RemoteTransferClient {

    /**
     * Estabelece a conexão com o servidor remoto.
     * @return true se conectado com sucesso; false caso contrário.
     */
    boolean connect();

    /**
     * Finaliza a conexão com o servidor remoto.
     */
    void disconnect();

    /**
     * Transfere arquivos do sistema local para o servidor remoto.
     * @param source Caminho local (arquivo, pasta ou wildcard).
     * @param target Caminho remoto de destino.
     * @param isMain Define se a operação deve ser executada (true) ou ignorada (false).
     * @return true se algum arquivo foi enviado com sucesso; false caso contrário.
     */
    boolean moveToRemote(String source, String target, boolean isMain);

    /**
     * Transfere arquivos do servidor remoto para o sistema local.
     * @param source Caminho remoto (arquivo, pasta ou wildcard).
     * @param target Caminho local de destino.
     * @param isMain Define se a operação deve ser executada (true) ou ignorada (false).
     * @return true se algum arquivo foi baixado com sucesso; false caso contrário.
     */
    boolean moveToLocal(String source, String target, boolean isMain);

    /**
     * Verifica se o cliente está conectado ao servidor remoto.
     * @return true se conectado; false caso contrário.
     */
    boolean isConnected();
}
