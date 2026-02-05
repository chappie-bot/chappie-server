# The Chappie server

## Windows Users: RAG Requirement (Optional)

If you're running on Windows and want **RAG (Retrieval-Augmented Generation)** capabilities, you need to install the **Microsoft Visual C++ Redistributable** for the ONNX Runtime (used by the BGE embedding model).

**Download:** https://aka.ms/vs/17/release/vc_redist.x64.exe

**Without this redistributable:**
- The server will start successfully
- RAG will be automatically disabled (you'll see a clear message in the logs)
- AI assistance will still work (just without documentation lookup)

**With the redistributable installed:**
- Full RAG capabilities enabled
- Documentation-aware AI assistance