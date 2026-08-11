package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.response.CategoriaDTO;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.repository.projection.ConteoCategoria;
import com.api.techmind_g9_team34.api_techmind.service.impl.CategoriaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceImplTest {

    @Mock
    private ContenidoAnalizadoRepository repository;

    private CategoriaService service;

    @BeforeEach
    void setUp() {
        service = new CategoriaServiceImpl(repository);
    }

    private ConteoCategoria conteo(String categoria, long cantidad) {
        return new ConteoCategoria() {
            @Override
            public String getCategoria() {
                return categoria;
            }

            @Override
            public long getCantidadProcesados() {
                return cantidad;
            }
        };
    }

    @Test
    void deberiaMapearConteosACategoriaDTOConCantidad() {
        given(repository.contarPorCategoria())
                .willReturn(List.of(conteo("Backend", 3), conteo("DevOps", 2)));

        List<CategoriaDTO> categorias = service.listarCategorias();

        assertThat(categorias).extracting(CategoriaDTO::categoria)
                .containsExactly("Backend", "DevOps");
        assertThat(categorias).extracting(CategoriaDTO::cantidadProcesados)
                .containsExactly(3L, 2L);
    }

    @Test
    void deberiaDevolverListaVaciaCuandoNoHayConteos() {
        given(repository.contarPorCategoria()).willReturn(List.of());

        List<CategoriaDTO> categorias = service.listarCategorias();

        assertThat(categorias).isEmpty();
    }
}