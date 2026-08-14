package gg.fotia.tags.gradient;

public interface GradientPaymentOperations {

    boolean available();

    boolean hasEnough();

    boolean withdraw();

    boolean refund();
}
