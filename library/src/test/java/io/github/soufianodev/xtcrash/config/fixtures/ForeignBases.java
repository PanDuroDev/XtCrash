package io.github.soufianodev.xtcrash.config.fixtures;

public final class ForeignBases {
    public static class ProtectedBase {
        protected ProtectedBase() {}
    }

    public static class PackageBase {
        PackageBase() {}
        public PackageBase(int ignored) {}
    }
}
