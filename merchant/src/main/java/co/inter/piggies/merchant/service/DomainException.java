package co.inter.piggies.merchant.service;

/** Erro de negócio mapeado para um status HTTP (resposta application/problem+json). */
public abstract class DomainException extends RuntimeException {

    private final int status;
    private final String title;

    protected DomainException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
