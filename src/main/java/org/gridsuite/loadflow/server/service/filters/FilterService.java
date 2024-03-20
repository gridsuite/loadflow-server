package org.gridsuite.loadflow.server.service.filters;

import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.springframework.stereotype.Service;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

//TODO: to delete after merging filter library
/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class FilterService {
    public static final String FILTER_END_POINT_EVALUATE = "/filters/evaluate";
    static final String FILTER_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    @Setter
    private String filterServerBaseUri;

    private final RestTemplate restTemplate;

    public FilterService(
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri,
            RestTemplate restTemplate) {
        this.filterServerBaseUri = filterServerBaseUri;
        this.restTemplate = restTemplate;
    }

    private String getFilterServerURI() {
        return this.filterServerBaseUri + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EVALUATE;
    }

    public String evaluateFilter(UUID networkUuid, String variantId, String filter) {
        Objects.requireNonNull(networkUuid);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(getFilterServerURI());
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        var uriComponent = uriComponentsBuilder
                .build();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(filter, headers);

        try {
            return restTemplate.postForObject(uriComponent.toUriString(), request, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new LoadflowException(LoadflowException.Type.NETWORK_NOT_FOUND);
            } else {
                throw new LoadflowException(LoadflowException.Type.EVALUATE_FILTER_FAILED);
            }
        }
    }
}
