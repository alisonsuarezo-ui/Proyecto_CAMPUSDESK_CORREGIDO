package campusdesk.controller;

import campusdesk.dto.DashboardMetricsDTO;
import campusdesk.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController).build();
        principal = () -> "superadmin@campusdesk.com";
    }

    @Test
    @DisplayName("CP-09 / RF-07: GET /api/dashboard/metrics retorna métricas consolidadas con 200 OK")
    void testGetMetrics() throws Exception {
        DashboardMetricsDTO metricsDTO = DashboardMetricsDTO.builder()
                .total(12)
                .abiertas(4)
                .asignadas(3)
                .enProceso(2)
                .resueltas(2)
                .cerradas(1)
                .build();

        when(dashboardService.getMetrics(eq("superadmin@campusdesk.com")))
                .thenReturn(metricsDTO);

        mockMvc.perform(get("/api/dashboard/metrics")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.abiertas").value(4))
                .andExpect(jsonPath("$.asignadas").value(3))
                .andExpect(jsonPath("$.enProceso").value(2))
                .andExpect(jsonPath("$.resueltas").value(2))
                .andExpect(jsonPath("$.cerradas").value(1));
    }
}
