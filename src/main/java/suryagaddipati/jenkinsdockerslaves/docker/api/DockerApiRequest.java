package suryagaddipati.jenkinsdockerslaves.docker.api;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.ActorMaterializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;
import suryagaddipati.jenkinsdockerslaves.docker.api.request.ApiRequest;
import suryagaddipati.jenkinsdockerslaves.docker.api.response.ApiError;
import suryagaddipati.jenkinsdockerslaves.docker.api.response.ApiSuccess;
import suryagaddipati.jenkinsdockerslaves.docker.api.response.SerializationException;
import suryagaddipati.jenkinsdockerslaves.docker.marshalling.Jackson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DockerApiRequest {
    private final ActorSystem as;
    private final ApiRequest apiRequest;
    private final ActorMaterializer materializer;

    public DockerApiRequest( ActorSystem as, ApiRequest request) {
        this.as = as;
        this.materializer = ActorMaterializer.create(as);
        this.apiRequest = request;
    }

    public CompletionStage<Either<SerializationException, ?>>  execute(){
        Either<SerializationException, String> marshallResult = marshall(apiRequest.getEntity());
        if(marshallResult.isRight()){
            String requestBody = marshallResult.right().get();
            return executeRequest(requestBody, apiRequest.getHttpRequest()).thenComposeAsync(response -> handleResponse(response));
        }
        return  CompletableFuture.completedFuture( marshallResult);
    }

    private  CompletionStage<Either<SerializationException,?>> handleResponse(HttpResponse response) {
        return response.status().isFailure() ? handleFailure(response) : handleSuccess(response);
    }

    private CompletionStage<Either<SerializationException, ?>> handleSuccess(HttpResponse httpResponse) {
        if(apiRequest.getResponseClass() != null){
            Unmarshaller<HttpEntity,  Either<SerializationException, ?>> unmarshaller = Jackson.unmarshaller(apiRequest.getResponseClass(), apiRequest.getResponseType());
            return  unmarshaller.unmarshal(httpResponse.entity(), materializer);
        }else{
            ApiSuccess value = new ApiSuccess(apiRequest.getClass(), httpResponse.entity());
            return CompletableFuture.completedFuture(new Right(value));
        }
    }

    private  CompletionStage<Either<SerializationException,?>> handleFailure(HttpResponse httpResponse) {
        if(httpResponse.status().intValue() == 500 ){
            ApiError apiError = new ApiError(apiRequest.getClass(), httpResponse.status(), httpResponse.entity().toString());
            return CompletableFuture.completedFuture(new Right(apiError));
        }
        Unmarshaller<HttpEntity,  Either<SerializationException,?>> unmarshaller = Jackson.unmarshaller(ErrorMessage.class);
        return  unmarshaller.unmarshal(httpResponse.entity(),materializer).thenApplyAsync(csr ->
                csr.map(err -> new ApiError(apiRequest.getClass(), httpResponse.status(), ((ErrorMessage)err).message))
        );
    }

    private Either<SerializationException,String> marshall(Object object){
        try {
            String jsonString = Jackson.getDefaultObjectMapper().writeValueAsString(object);
            return new Right(jsonString);
        } catch (JsonProcessingException e) {
            return new Left(new SerializationException(e));
        }

    }
    private CompletionStage< HttpResponse> executeRequest(String requestEntity, HttpRequest request) {
        return Http.get(as).singleRequest(request.withEntity(ContentTypes.APPLICATION_JSON, requestEntity), materializer);
    }

    private static class ErrorMessage{
        public String message;
    }
}
