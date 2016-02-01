/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.document.Field;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.array.DynamicArrayFieldMapperBuilderFactory;
import org.elasticsearch.index.mapper.object.ArrayValueMapperParser;
import org.elasticsearch.index.mapper.object.DynamicValueMapperLookup;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.elasticsearch.index.mapper.object.ObjectMapperProxy;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * fieldmapper for encoding and handling arrays explicitly
 *
 * handler for type "array".
 *
 * accepts mappings like:
 *
 *  "array_field": {
 *      "type": "array",
 *      "inner": {
 *          "type": "boolean",
 *          "null_value": true
 *      }
 *  }
 *
 *  This would be parsed as a array of booleans.
 *  This field now only accepts arrays, no single values.
 *  So inserting a document like:
 *
 *  {
 *      "array_field": true
 *  }
 *
 *  will fail, while a document like:
 *
 *  {
 *      "array_field": [true]
 *  }
 *
 *  will pass.
 *
 */
public class ArrayMapper extends FieldMapper implements ArrayValueMapperParser {

    public static final String CONTENT_TYPE = "array";
    public static final String INNER_TYPE = "inner";
    public static final XContentBuilderString INNER = new XContentBuilderString(INNER_TYPE);
    private Mapper innerMapper;
    private InnerParser innerParser;

    /**
     * inner class used to parse single values from arrays
     * for non-object fields
     */
    private class InnerParser {
        protected final String fieldName;

        private InnerParser(String fieldName) {
            this.fieldName = fieldName;
        }

        public Mapper parse(FieldMapper mapper, ParseContext ctx) throws IOException {
            return mapper.parse(ctx);
        }

        public Mapper parse(ObjectMapper mapper, ParseContext ctx) throws IOException {
            return DocumentParser.parseObject(ctx, mapper, true);
        }
    }

    /**
     * used to parse single object values from arrays
     * the current field name must be set when traversing the inner objectMapper
     */
    private class InnerObjectParser extends InnerParser {

        private InnerObjectParser(String fieldName) {
            super(fieldName);
        }

        @Override
        public Mapper parse(ObjectMapper mapper, ParseContext ctx) throws IOException {
            ctx.path().add(fieldName);
            Mapper update = super.parse(mapper, ctx);
            ctx.path().remove();
            return update;
        }
    }

    public static class Builder extends FieldMapper.Builder<Builder, ArrayMapper> {

        private Mapper.Builder innerMapperBuilder;

         public static class BuilderFactory implements DynamicArrayFieldMapperBuilderFactory {
             public Builder create(String name){
                 return new Builder(name);
             }
         }

        public Builder(String name) {
            super(name, new ArrayFieldType());
        }

        public Builder innerMapperBuilder(Mapper.Builder builder) {
            this.innerMapperBuilder = builder;
            return this;
        }

        @Override
        public ArrayMapper build(BuilderContext context) {
            ContentPath.Type origPathType = context.path().pathType();
            context.path().pathType(ContentPath.Type.FULL);

            context.path().add(name);
            context.path().remove();

            context.path().pathType(origPathType);
            setupFieldType(context);

            if (innerMapperBuilder == null) {
                return new ArrayMapper(name, fieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
            } else {
                Mapper innerMapper = innerMapperBuilder.build(context);
                return new ArrayMapper(innerMapper, name, fieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
            }
        }
    }

    static class ArrayFieldType extends MappedFieldType {

        public ArrayFieldType() {}

        protected ArrayFieldType(ArrayFieldType ref) {
            super(ref);
        }

        @Override
        public MappedFieldType clone() {
            return new ArrayFieldType(this);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            Builder builder = new Builder(name);
            boolean innerFound = false;
            for (Iterator<Map.Entry<String, Object>> iterator = node.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, Object> entry = iterator.next();
                String fieldName = Strings.toUnderscoreCase(entry.getKey());
                Object fieldNode = entry.getValue();
                if (fieldName.equals(INNER_TYPE)) {
                    innerFound = true;
                    if (fieldNode == null || !(fieldNode instanceof Map)) {
                        throw new MapperParsingException("property [inner] must be a map");
                    }
                    Map<String, Object> innerNode = (Map<String, Object>)fieldNode;
                    String typeName = (String)innerNode.get("type");
                    if (typeName == null && innerNode.containsKey("properties")) {
                        typeName = ObjectMapper.CONTENT_TYPE;
                    } else if (CONTENT_TYPE.equalsIgnoreCase(typeName)) {
                        throw new MapperParsingException("nested arrays are not supported");
                    }

                    Mapper.TypeParser innerTypeParser = parserContext.typeParser(typeName);

                    Mapper.Builder innerBuilder = innerTypeParser.parse(name, innerNode, parserContext);
                    builder.innerMapperBuilder(innerBuilder);
                    iterator.remove();
                }
            }
            if (!innerFound) {
                throw new MapperParsingException("property [inner] missing");
            }
            return builder;
        }
    }

    private final String name;

    /**
     * only called when creating a new ArrayMapper for a dynamic array
     * whose inner type is not known yet.
     *
     * The {@linkplain #innerMapper} and {@linkplain #innerParser} will be set
     * when calling {@linkplain #parse(org.elasticsearch.index.mapper.ParseContext)}.
     *
     */
    private ArrayMapper(String name, MappedFieldType fieldType, Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(name, fieldType, new ArrayFieldType(), indexSettings, multiFields, copyTo);
        this.name = name;
    }

    public ArrayMapper(Mapper innerMapper, String name, MappedFieldType fieldType, Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        this(name, fieldType, indexSettings, multiFields, copyTo);
        this.innerMapper = innerMapper;
        setInnerParser();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Mapper parse(ParseContext context) throws IOException {
        XContentParser parser = context.parser();
        XContentParser.Token token = parser.currentToken();
        if (token == XContentParser.Token.VALUE_NULL) {
            parseNull(context);
            return null;
        } else if  (token != XContentParser.Token.START_ARRAY) {
            throw new ElasticsearchParseException("invalid array");
        }
        token = parser.nextToken();
        Boolean updatedMapping = false;
        if (innerMapper == null) {
            // this is the case for new dynamic arrays

            // skip nulls for detection
            while (token == XContentParser.Token.VALUE_NULL) {
                token = parser.nextToken();
            }
            // nested arrays
            if (token == XContentParser.Token.START_ARRAY) {
                throw new ElasticsearchParseException("nested arrays are not supported");
            } else if  (token == XContentParser.Token.END_ARRAY) {
                // empty or null arrays -- cannot guess
                throw new ElasticsearchParseException("unable to guess type");
            }

            Mapper mapper = DynamicValueMapperLookup.getMapper(context, name(), token);
            if (mapper == null) {
                // no mapper detected -- cannot guess
                throw new ElasticsearchParseException("unable to guess type");
            }
            innerMapper = mapper;
            setInnerParser();
            updatedMapping = true;
        }

        while (token != XContentParser.Token.END_ARRAY) {
            Mapper update = null;

            // we only get here for non-empty arrays
            if (innerMapper instanceof FieldMapper) {
                update = innerParser.parse((FieldMapper)innerMapper, context);
            } else if (innerMapper instanceof ObjectMapper) {
                update = innerParser.parse((ObjectMapper)innerMapper, context);
            }

            if (update != null) {
                MapperUtils.merge(innerMapper, update);
                updatedMapping = true;
            }

            token = parser.nextToken();
        }

        return updatedMapping ? this : null;
    }

    private void setInnerParser() {
        if (innerParser != null && innerMapper instanceof ObjectMapper) {
            this.innerParser = new InnerObjectParser(this.name);
        } else {
            this.innerParser = new InnerParser(name);
        }
    }

    private void parseNull(ParseContext context) throws IOException {
        assert innerMapper != null : "should only end up here, if mapper is not dynamic, so innerMapper is not null";
        innerParser.parse((FieldMapper)innerMapper, context);
    }


    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(innerMapper.name());
        builder.field("type", contentType());
        builder.startObject(INNER);

        if (innerMapper instanceof FieldMapper) {
            boolean includeDefaults = params.paramAsBoolean("include_defaults", false);
            ((FieldMapper)innerMapper).doXContentBody(builder, includeDefaults, params);
        } else if (innerMapper instanceof ObjectMapper) {
            ObjectMapperProxy objectMapperProxy = new ObjectMapperProxy((ObjectMapper)innerMapper);
            objectMapperProxy.toXContent(builder, params, null);
        }

        return builder.endObject();
    }

    public void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException {
        innerMapper.merge(mergeWith, mergeResult);
    }

    public Iterator<Mapper> iterator() {
        return innerMapper.iterator();
    }

    @Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
        throw new UnsupportedOperationException();
    }
}
