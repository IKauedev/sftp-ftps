package org.sftp;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.function.BiFunction;

public class SFTP implements RemoteTransferClient {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private Session session;
    private ChannelSftp channelSftp;
    private boolean isConnected = false;

    public SFTP(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public boolean connect() {
        if (isConnected) return true;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;

            isConnected = true;
            System.out.println("Conectado ao servidor SFTP.");
            return true;

        } catch (Exception e) {
            System.err.println("Erro ao conectar ao servidor SFTP: " + e.getMessage());
            e.printStackTrace();
            isConnected = false;
            return false;
        }
    }

    public void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
            System.out.println("Canal SFTP desconectado.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Sessão SFTP desconectada.");
        }
        isConnected = false;
    }

    public boolean moveToLocal(String source, String target, boolean isMain) {
        if (!isMain) {
            System.out.println("Operação ignorada: isMain = false");
            return false;
        }

        try {
            if (!connect()) {
                System.err.println("Falha ao conectar.");
                return false;
            }

            source = normalizePath(source);
            target = normalizePath(target);

            boolean hasWildcard = source.contains("*");
            String remoteBaseDir;
            Pattern pattern;

            if (hasWildcard) {
                int lastSlash = source.lastIndexOf("/");
                remoteBaseDir = (lastSlash >= 0) ? source.substring(0, lastSlash) : ".";
                String filePattern = source.substring(lastSlash + 1)
                        .replace(".", "\\.")
                        .replace("*", ".*");
                pattern = Pattern.compile(filePattern);
            } else {
                int lastSlash = source.lastIndexOf("/");
                remoteBaseDir = (lastSlash >= 0) ? source.substring(0, lastSlash) : ".";
                String fileName = source.substring(lastSlash + 1);
                pattern = Pattern.compile(Pattern.quote(fileName));
            }

            final File localTarget = new File(target);
            if (!localTarget.exists() && !localTarget.mkdirs()) {
                System.err.println("Falha ao criar diretório local base: " + localTarget.getAbsolutePath());
                return false;
            }

            final boolean[] anySuccess = {false};

            BiFunction<String, File, Boolean> downloadRecursive = new BiFunction<String, File, Boolean>() {
                @Override
                public Boolean apply(String remoteDir, File localDir) {
                    try {
                        if (!localDir.exists() && !localDir.mkdirs()) {
                            System.err.println("Erro ao criar diretório local: " + localDir.getAbsolutePath());
                            return false;
                        }

                        @SuppressWarnings("unchecked")
                        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(remoteDir);
                        boolean success = false;

                        for (ChannelSftp.LsEntry entry : entries) {
                            String name = entry.getFilename();
                            if (name.equals(".") || name.equals("..")) continue;

                            String remotePath = remoteDir + "/" + name;
                            File localPath = new File(localDir, name);

                            if (entry.getAttrs().isDir()) {
                                if (this.apply(remotePath, localPath)) success = true;
                            } else {
                                if (pattern.matcher(name).matches()) {
                                    OutputStream os = null;
                                    try {
                                        os = new FileOutputStream(localPath);
                                        channelSftp.get(remotePath, os);
                                        System.out.println("Download concluído: " + remotePath + " → " + localPath.getAbsolutePath());
                                        channelSftp.rm(remotePath);
                                        success = true;
                                    } catch (Exception e) {
                                        System.err.println("Erro ao transferir: " + remotePath + " → " + e.getMessage());
                                    } finally {
                                        if (os != null) try { os.close(); } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                        return success;
                    } catch (Exception e) {
                        System.err.println("Erro no download recursivo: " + e.getMessage());
                        return false;
                    }
                }
            };

            anySuccess[0] = downloadRecursive.apply(remoteBaseDir, localTarget);
            return anySuccess[0];

        } catch (Exception e) {
            System.err.println("Erro geral moveToLocal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean moveToRemote(final String source, final String target, boolean isMain) {
        if (!isMain) {
            System.out.println("Operação ignorada: isMain = false");
            return false;
        }

        try {
            if (!connect()) {
                System.err.println("Falha ao conectar.");
                return false;
            }

            String normalizedSource = normalizePath(source);
            String normalizedTarget = normalizePath(target);

            File localSource = new File(normalizedSource);
            if (!localSource.exists()) {
                System.err.println("Arquivo ou diretório local não existe: " + source);
                return false;
            }

            final boolean targetIsDir = normalizedTarget.endsWith("/");

            final String finalTarget = normalizedTarget;

            Runnable ensureRemoteDirectories = new Runnable() {
                @Override
                public void run() {
                    try {
                        String[] folders = finalTarget.split("/");
                        String currentPath = "";
                        for (String folder : folders) {
                            if (folder.isEmpty()) continue;
                            currentPath += "/" + folder;
                            try {
                                channelSftp.cd(currentPath);
                            } catch (SftpException e) {
                                try {
                                    channelSftp.mkdir(currentPath);
                                    System.out.println("Criado diretório remoto: " + currentPath);
                                } catch (SftpException ex) {
                                    System.err.println("Erro ao criar diretório remoto " + currentPath + ": " + ex.getMessage());
                                    throw ex;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Erro criando diretórios remotos: " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                }
            };

            BiFunction<File, String, Boolean> uploadRecursive = new BiFunction<File, String, Boolean>() {
                @Override
                public Boolean apply(File localFile, String remotePath) {
                    boolean success = false;
                    try {
                        if (localFile.isDirectory()) {
                            try {
                                channelSftp.cd(remotePath);
                            } catch (SftpException e) {
                                try {
                                    channelSftp.mkdir(remotePath);
                                    System.out.println("Criado diretório remoto: " + remotePath);
                                } catch (SftpException ex) {
                                    System.err.println("Erro criando diretório remoto: " + ex.getMessage());
                                    return false;
                                }
                            }
                            File[] files = localFile.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    String remoteSubPath = remotePath + "/" + f.getName();
                                    success |= this.apply(f, remoteSubPath);
                                }
                            }
                        } else {
                            FileInputStream fis = null;
                            try {
                                fis = new FileInputStream(localFile);
                                channelSftp.put(fis, remotePath);
                                System.out.println("Upload concluído: " + localFile.getAbsolutePath() + " → " + remotePath);
                                success = true;
                            } catch (Exception e) {
                                System.err.println("Erro no upload do arquivo: " + localFile.getAbsolutePath() + " → " + e.getMessage());
                            } finally {
                                if (fis != null) try { fis.close(); } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Erro no upload recursivo: " + e.getMessage());
                    }
                    return success;
                }
            };

            boolean anySuccess = false;

            if (localSource.isDirectory()) {
                String remoteDir = targetIsDir ? normalizedTarget.replaceAll("/+$", "") : normalizedTarget;
                anySuccess = uploadRecursive.apply(localSource, remoteDir);
            } else if (source.contains("*")) {
                int lastSlash = normalizedSource.lastIndexOf("/");
                String localDirPath = (lastSlash >= 0) ? normalizedSource.substring(0, lastSlash) : ".";
                String patternText = normalizedSource.substring(lastSlash + 1)
                        .replace(".", "\\.")
                        .replace("*", ".*");
                final Pattern pattern = Pattern.compile(patternText);

                File localDir = new File(localDirPath);
                if (!localDir.exists() || !localDir.isDirectory()) {
                    System.err.println("Diretório local para wildcard inválido: " + localDirPath);
                    return false;
                }

                ensureRemoteDirectories.run();

                File[] files = localDir.listFiles((dir, name) -> pattern.matcher(name).matches());
                if (files != null) {
                    for (File f : files) {
                        FileInputStream fis = null;
                        String remoteFilePath = targetIsDir ? normalizedTarget + f.getName() : normalizedTarget;
                        try {
                            fis = new FileInputStream(f);
                            channelSftp.put(fis, remoteFilePath);
                            System.out.println("Upload concluído: " + f.getAbsolutePath() + " → " + remoteFilePath);
                            anySuccess = true;
                        } catch (Exception e) {
                            System.err.println("Erro no upload do arquivo: " + f.getAbsolutePath() + " → " + e.getMessage());
                        } finally {
                            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
                        }
                    }
                }
            } else {
                ensureRemoteDirectories.run();
                String remoteFilePath = targetIsDir ? normalizedTarget + localSource.getName() : normalizedTarget;
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(localSource);
                    channelSftp.put(fis, remoteFilePath);
                    System.out.println("Upload concluído: " + localSource.getAbsolutePath() + " → " + remoteFilePath);
                    anySuccess = true;
                } catch (Exception e) {
                    System.err.println("Erro no upload do arquivo: " + e.getMessage());
                } finally {
                    if (fis != null) try { fis.close(); } catch (Exception ignored) {}
                }
            }

            return anySuccess;

        } catch (Exception e) {
            System.err.println("Erro geral moveToRemote: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String normalizePath(String path) {
        return path.replace("\\", "/").replaceAll("/+", "/").trim();
    }

    public boolean isConnected() {
        return isConnected;
    }
}