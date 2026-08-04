package com.api.techmind_g9_team34.api_techmind.config;

import com.api.techmind_g9_team34.api_techmind.exception.ValidacionException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageableValidatingResolverTest {

    private final PageableValidatingResolver resolver = new PageableValidatingResolver();

    private NativeWebRequest webRequest(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (query != null && !query.isBlank()) {
            request.setQueryString(query);
            for (String par : query.split("&")) {
                String[] kv = par.split("=", 2);
                request.addParameter(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return new ServletWebRequest(request);
    }

    private PageRequest resolver(String query) {
        return (PageRequest) resolver.resolveArgument(null, null, webRequest(query), null);
    }

    @Test
    void deberiaUsarValoresPorDefectoCuandoNoHayParametros() {
        PageRequest pageable = resolver(null);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(PageableValidatingResolver.DEFAULT_SIZE, pageable.getPageSize());
    }

    @Test
    void deberiaAplicarPageYSize() {
        PageRequest pageable = resolver("page=2&size=10");
        assertEquals(2, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
    }

    @Test
    void deberiaRechazarSizeMayorAlMaximo() {
        assertThrows(ValidacionException.class, () -> resolver("size=51"));
    }

    @Test
    void deberiaRechazarSizeNegativo() {
        assertThrows(ValidacionException.class, () -> resolver("size=-5"));
    }

    @Test
    void deberiaRechazarPageNegativo() {
        assertThrows(ValidacionException.class, () -> resolver("page=-1"));
    }

    @Test
    void deberiaRechazarSortFueraDeWhitelist() {
        assertThrows(ValidacionException.class, () -> resolver("sort=probabilidad,desc"));
    }

    @Test
    void deberiaAplicarSortPermitidoAscendente() {
        PageRequest pageable = resolver("sort=titulo,asc");
        assertEquals("titulo", pageable.getSort().iterator().next().getProperty());
        assertEquals(org.springframework.data.domain.Sort.Direction.ASC,
                pageable.getSort().iterator().next().getDirection());
    }
}