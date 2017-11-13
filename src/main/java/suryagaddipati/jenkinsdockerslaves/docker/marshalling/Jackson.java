
package suryagaddipati.jenkinsdockerslaves.docker.marshalling;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;
import suryagaddipati.jenkinsdockerslaves.docker.api.response.SerializationException;

import java.io.IOException;
import java.util.List;

public class Jackson {

  private static final ObjectMapper defaultObjectMapper = new ObjectMapper();
  static{
    defaultObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    defaultObjectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    defaultObjectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    defaultObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
  }


  public static  Unmarshaller<HttpEntity, Either<SerializationException,?>> unmarshaller(Class<?> expectedType) {
    return unmarshaller(defaultObjectMapper, expectedType);
  }

  public static  Unmarshaller<HttpEntity, Either<SerializationException,?>> unmarshaller(ObjectMapper mapper, Class<?> expectedType) {
    return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
            .thenApply(s -> fromJSON(mapper, s, expectedType));
  }

    private static <T> Either< SerializationException,T>  fromJSON(ObjectMapper mapper, String json, Class<T> expectedType) {
    try {
      return new  Right( mapper.readerFor(expectedType).readValue(json));
    } catch (IOException e) {
        return new Left( new SerializationException(e));
    }
  }

  private static <T> Either< SerializationException,T>  fromJSONArray(ObjectMapper mapper, String json, Class<T> expectedType) {
    try {
      CollectionType arrayType = mapper.getTypeFactory()
              .constructCollectionType(List.class, expectedType);
      return new Right( mapper.readerFor(arrayType).readValue(json));
    } catch (IOException e) {
        return new Left( new SerializationException(e));
    }
  }


  public static   Unmarshaller<HttpEntity, Either<SerializationException,?>>  unmarshaller(Class<?> responseClass, ResponseType responseType) {
    if(responseType == ResponseType.CLASS){
      return unmarshaller(responseClass);
    }else {
      return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
              .thenApply(s -> fromJSONArray(defaultObjectMapper, s, responseClass));
    }
  }
  public static ObjectMapper getDefaultObjectMapper() {
    return defaultObjectMapper;
  }
}
